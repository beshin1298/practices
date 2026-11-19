package com.warden.user.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.warden.common.error.AppException;
import com.warden.common.error.ErrorCode;
import com.warden.common.util.Normalizer;
import com.warden.common.web.PageResponse;
import com.warden.department.entity.Department;
import com.warden.department.repository.DepartmentRepository;
import com.warden.role.constant.Permissions;
import com.warden.role.entity.Permission;
import com.warden.role.entity.Role;
import com.warden.role.repository.PermissionRepository;
import com.warden.role.repository.RoleRepository;
import com.warden.user.dto.request.CreateUserRequest;
import com.warden.user.dto.request.UpdateUserRequest;
import com.warden.user.dto.response.UserResponse;
import com.warden.user.entity.User;
import com.warden.user.event.UserSecurityChangedEvent;
import com.warden.user.mapper.UserMapper;
import com.warden.user.repository.UserRepository;
import com.warden.user.repository.UserSpecifications;
import com.warden.user.service.Actor;
import com.warden.user.service.UserAccessPolicy;
import com.warden.user.service.UserManagementService;

/** Default {@link UserManagementService}. */
@Service
@Transactional
public class UserManagementServiceImpl implements UserManagementService {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    static final int MAX_PAGE_SIZE = 100;
    /** Client sort key -> entity path. Only these are accepted (never passwordHash, tokenVersion). */
    static final Map<String, String> SORTABLE = Map.of(
            "username", "username",
            "email", "email",
            "fullName", "fullName",
            "createdAt", "createdAt",
            "lastLoginAt", "lastLoginAt");

    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final DepartmentRepository departments;
    private final UserAccessPolicy policy;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    public UserManagementServiceImpl(UserRepository users, RoleRepository roles, PermissionRepository permissions,
            DepartmentRepository departments, UserAccessPolicy policy, UserMapper userMapper,
            PasswordEncoder passwordEncoder, ApplicationEventPublisher events) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.departments = departments;
        this.policy = policy;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Actor actor, String query, Long departmentId, int page, int size,
            String sort, String direction) {
        String property = SORTABLE.getOrDefault(sort, "createdAt");
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(dir, property).and(Sort.by("id")));

        Specification<User> filter = Specification.allOf(
                UserSpecifications.matches(query),
                UserSpecifications.inAnyDepartment(policy.visibleDepartments(actor, departmentId)));
        return PageResponse.of(users.findAll(filter, pageable), userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse get(Actor actor, Long id) {
        User user = load(id);
        // Out of scope looks exactly like "does not exist": a manager cannot probe other departments' ids.
        if (!policy.canRead(actor, UserAccessPolicy.subjectOf(user))) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse create(Actor actor, CreateUserRequest request) {
        List<Role> newRoles = rolesById(request.roleIds());
        policy.checkCreate(actor, maxRank(newRoles), permissionsOf(newRoles));

        String username = Normalizer.key(request.username());
        String email = Normalizer.key(request.email());
        if (users.existsByUsername(username)) {
            throw new AppException(ErrorCode.USERNAME_TAKEN);
        }
        if (users.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_TAKEN);
        }
        User user = new User(username, email, request.fullName().trim(), passwordEncoder.encode(request.password()));
        user.setPhone(blankToNull(request.phone()));
        user.setJobTitle(blankToNull(request.jobTitle()));
        user.replaceRoles(newRoles);
        user.replaceDepartments(departmentsById(request.departmentIds()));
        User saved = users.save(user);
        audit.info("action=user_created actorId={} userId={} roles={}", actor.id(), saved.getId(), request.roleIds());
        return userMapper.toResponse(saved);
    }

    @Override
    public UserResponse update(Actor actor, Long id, UpdateUserRequest request) {
        User user = load(id);
        String email = Normalizer.key(request.email());
        boolean changesEmail = !email.equals(user.getEmail());
        policy.checkUpdate(actor, UserAccessPolicy.subjectOf(user), changesEmail);

        if (changesEmail && users.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_TAKEN);
        }
        user.setFullName(request.fullName().trim());
        user.setPhone(blankToNull(request.phone()));
        user.setJobTitle(blankToNull(request.jobTitle()));
        user.setEmail(email);
        audit.info("action=user_updated actorId={} userId={} emailChanged={}", actor.id(), id, changesEmail);
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse assignDepartments(Actor actor, Long id, Set<Long> departmentIds) {
        User user = load(id);
        policy.checkDepartmentChange(actor, UserAccessPolicy.subjectOf(user));
        user.replaceDepartments(departmentsById(departmentIds));
        audit.info("action=departments_changed actorId={} userId={} departments={}", actor.id(), id, departmentIds);
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse assignRoles(Actor actor, Long id, Set<Long> roleIds) {
        User user = load(id);
        List<Role> newRoles = rolesById(roleIds);
        Set<String> before = permissionsOf(user.getRoles());
        policy.checkRoleChange(actor, UserAccessPolicy.subjectOf(user), maxRank(newRoles),
                Permissions.changed(before, permissionsOf(newRoles)));
        if (maxRank(newRoles) < user.rank()) {
            requireAnotherTopRankedUser(user);
        }
        List<String> oldCodes = user.getRoles().stream().map(Role::getCode).toList();
        user.replaceRoles(newRoles);
        audit.info("action=roles_changed actorId={} userId={} before={} after={}", actor.id(), id, oldCodes,
                newRoles.stream().map(Role::getCode).toList());
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse changeStatus(Actor actor, Long id, boolean enabled) {
        User user = load(id);
        policy.checkStatusChange(actor, UserAccessPolicy.subjectOf(user));
        if (!enabled && user.isEnabled()) {
            requireAnotherTopRankedUser(user);
        }
        user.setEnabled(enabled);
        audit.info("action=status_changed actorId={} userId={} enabled={}", actor.id(), id, enabled);
        if (!enabled) {
            events.publishEvent(new UserSecurityChangedEvent(id, UserSecurityChangedEvent.Reason.DISABLED));
        }
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse assignPermissions(Actor actor, Long id, Set<String> permissionCodes) {
        User user = load(id);
        List<Permission> granted = permissions.findAllByCodeOrThrow(permissionCodes);
        List<String> before = user.directPermissionCodes();
        policy.checkPermissionGrant(actor, UserAccessPolicy.subjectOf(user), Permissions.changed(before, permissionCodes));
        user.replaceDirectPermissions(granted);
        audit.info("action=direct_permissions_changed actorId={} userId={} before={} after={}", actor.id(), id, before,
                user.directPermissionCodes());
        return userMapper.toResponse(user);
    }

    private static Set<String> permissionsOf(Collection<Role> roles) {
        return roles.stream().flatMap(role -> role.permissionCodes().stream()).collect(Collectors.toSet());
    }

    /**
     * Disabling or demoting a top-ranked user must leave another enabled one. The lock on the top role
     * row serializes concurrent attempts (two admins acting on each other), so the count is reliable.
     */
    private void requireAnotherTopRankedUser(User target) {
        int topRank = roles.lockTopRoles().stream().mapToInt(Role::getRank).max().orElse(Integer.MAX_VALUE);
        if (target.rank() == topRank && users.countEnabledWithRankExcept(topRank, target.getId()) == 0) {
            throw new AppException(ErrorCode.LAST_TOP_USER);
        }
    }

    private User load(Long id) {
        return users.findForAuthenticationById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private List<Role> rolesById(Set<Long> ids) {
        List<Role> found = roles.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new AppException(ErrorCode.ROLE_NOT_FOUND);
        }
        return found;
    }

    private List<Department> departmentsById(Set<Long> ids) {
        List<Department> found = departments.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new AppException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        return found;
    }

    private static int maxRank(List<Role> roles) {
        return roles.stream().mapToInt(Role::getRank).max().orElse(-1);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
