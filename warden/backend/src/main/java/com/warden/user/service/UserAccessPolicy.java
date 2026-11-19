package com.warden.user.service;

import static com.warden.role.constant.Permissions.USER_ASSIGN_ROLE;
import static com.warden.role.constant.Permissions.USER_CREATE;
import static com.warden.role.constant.Permissions.USER_DISABLE;
import static com.warden.role.constant.Permissions.USER_READ_ALL;
import static com.warden.role.constant.Permissions.USER_READ_DEPARTMENT;
import static com.warden.role.constant.Permissions.USER_READ_OWN;
import static com.warden.role.constant.Permissions.USER_UPDATE_ALL;
import static com.warden.role.constant.Permissions.USER_UPDATE_DEPARTMENT;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.warden.common.error.AppException;
import com.warden.common.error.ErrorCode;
import com.warden.user.entity.User;

/**
 * Row-level authorization: WHICH users an actor may read or change. Whether the actor holds the
 * permission at all is checked earlier by the URL rules and @PreAuthorize; this class adds the
 * scope, and is the only place that does (single responsibility):
 * <ul>
 *   <li>DEPARTMENT scope: reading reaches everyone sharing at least one department with the actor;
 *       changing also needs the target to be ranked below the actor.</li>
 *   <li>ALL scope: anyone not ranked above the actor (HR cannot edit an ADMIN).</li>
 *   <li>Nobody grants a role above their own; nobody changes their own roles or status.</li>
 * </ul>
 */
@Component
public class UserAccessPolicy {

    public boolean canRead(Actor actor, AccessSubject target) {
        return actor.has(USER_READ_ALL)
                || (actor.has(USER_READ_DEPARTMENT) && shareDepartment(actor, target))
                || (actor.has(USER_READ_OWN) && isSelf(actor, target));
    }

    public boolean canUpdate(Actor actor, AccessSubject target) {
        if (actor.has(USER_UPDATE_ALL)) {
            return target.rank() <= actor.rank();
        }
        return actor.has(USER_UPDATE_DEPARTMENT) && shareDepartment(actor, target) && actor.rank() > target.rank();
    }

    public void checkRead(Actor actor, AccessSubject target) {
        require(canRead(actor, target));
    }

    /** Email is an HR-managed field: changing it needs company-wide update rights. */
    public void checkUpdate(Actor actor, AccessSubject target, boolean changesEmail) {
        require(canUpdate(actor, target) && (!changesEmail || actor.has(USER_UPDATE_ALL)));
    }

    public void checkDepartmentChange(Actor actor, AccessSubject target) {
        require(actor.has(USER_UPDATE_ALL) && target.rank() <= actor.rank());
    }

    /** The new account's roles may not rank above you, nor carry a permission you do not hold. */
    public void checkCreate(Actor actor, int newUserRank, Set<String> grantedCodes) {
        require(actor.has(USER_CREATE) && newUserRank <= actor.rank() && actor.holdsAll(grantedCodes));
    }

    public void checkStatusChange(Actor actor, AccessSubject target) {
        requireNotSelf(actor, target);
        require(actor.has(USER_DISABLE) && target.rank() <= actor.rank());
    }

    /**
     * Not on yourself; the roles may not rank above you; and every permission the change adds or
     * removes (through the roles) must be one you hold. That a top-ranked user always remains is
     * enforced by UserManagementServiceImpl under a lock, since two admins could act on each other.
     */
    public void checkRoleChange(Actor actor, AccessSubject target, int newRank, Set<String> changedCodes) {
        requireNotSelf(actor, target);
        require(actor.has(USER_ASSIGN_ROLE) && target.rank() <= actor.rank() && newRank <= actor.rank()
                && actor.holdsAll(changedCodes));
    }

    /**
     * Direct permissions ("quyền lẻ"): same guard as roles (not yourself, target not ranked above you),
     * plus the actor must hold every permission being added or removed.
     */
    public void checkPermissionGrant(Actor actor, AccessSubject target, Set<String> changedCodes) {
        requireNotSelf(actor, target);
        require(actor.has(USER_ASSIGN_ROLE) && target.rank() <= actor.rank() && actor.holdsAll(changedCodes));
    }

    /**
     * Departments the actor may list. {@code null} = no restriction (company-wide readers).
     * Department-scoped readers only ever see their own departments, whatever filter they send.
     */
    public Set<Long> visibleDepartments(Actor actor, Long requestedDepartmentId) {
        if (actor.has(USER_READ_ALL)) {
            return requestedDepartmentId == null ? null : Set.of(requestedDepartmentId);
        }
        if (requestedDepartmentId == null) {
            return actor.departmentIds();
        }
        return actor.departmentIds().contains(requestedDepartmentId) ? Set.of(requestedDepartmentId) : Collections.emptySet();
    }

    public static AccessSubject subjectOf(User user) {
        return new Subject(user.getId(), user.rank(), user.departmentIds());
    }

    private static boolean isSelf(AccessSubject actor, AccessSubject target) {
        return Objects.equals(actor.id(), target.id());
    }

    private static boolean shareDepartment(AccessSubject actor, AccessSubject target) {
        return !Collections.disjoint(actor.departmentIds(), target.departmentIds());
    }

    private static void requireNotSelf(AccessSubject actor, AccessSubject target) {
        if (isSelf(actor, target)) {
            throw new AppException(ErrorCode.SELF_MODIFICATION);
        }
    }

    private static void require(boolean allowed) {
        if (!allowed) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private record Subject(Long id, int rank, Set<Long> departmentIds) implements AccessSubject {
    }
}
