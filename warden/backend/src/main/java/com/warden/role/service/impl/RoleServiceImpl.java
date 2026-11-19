package com.warden.role.service.impl;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.warden.common.error.AppException;
import com.warden.common.error.ErrorCode;
import com.warden.role.constant.Permissions;
import com.warden.role.dto.response.RoleResponse;
import com.warden.role.entity.Permission;
import com.warden.role.entity.Role;
import com.warden.role.mapper.RoleMapper;
import com.warden.role.repository.PermissionRepository;
import com.warden.role.repository.RoleRepository;
import com.warden.role.service.RoleService;
import com.warden.user.service.Actor;

/** Default {@link RoleService}. */
@Service
@Transactional(readOnly = true)
public class RoleServiceImpl implements RoleService {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RoleMapper roleMapper;

    public RoleServiceImpl(RoleRepository roles, PermissionRepository permissions, RoleMapper roleMapper) {
        this.roles = roles;
        this.permissions = permissions;
        this.roleMapper = roleMapper;
    }

    @Override
    public List<RoleResponse> listRoles() {
        return roleMapper.toResponses(roles.findAllByOrderByRankAsc());
    }

    @Override
    @Transactional
    public RoleResponse updatePermissions(Actor actor, Long roleId, Set<String> permissionCodes) {
        // The role is the path resource here: a missing one is 404, not a bad request body.
        Role role = roles.findWithPermissionsById(roleId).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        if (role.getRank() >= actor.rank()) {
            throw new AppException(ErrorCode.ROLE_LOCKED);
        }
        List<Permission> granted = permissions.findAllByCodeOrThrow(permissionCodes);

        List<String> before = role.permissionCodes();
        if (!actor.holdsAll(Permissions.changed(before, permissionCodes))) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        role.replacePermissions(granted);
        audit.info("action=role_permissions_changed actorId={} role={} before={} after={}", actor.id(), role.getCode(),
                before, role.permissionCodes());
        return roleMapper.toResponse(role);
    }
}
