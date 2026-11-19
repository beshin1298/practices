package com.warden.role.service;

import java.util.List;
import java.util.Set;

import com.warden.role.dto.response.RoleResponse;
import com.warden.user.service.Actor;

/** Read side of roles: the reference list shown in forms and on the "Roles & permissions" screen. */
public interface RoleService {

    /** All roles from the lowest rank up, each with its permission codes (one query, entity graph). */
    List<RoleResponse> listRoles();

    /**
     * Replaces the permissions of one role. Only roles ranked below the actor (so the top role, ADMIN,
     * can never be changed and nobody locks everyone out), and only permissions the actor holds may be
     * added or removed. Takes effect on every holder's next request: the JWT filter reads permissions
     * from the database.
     */
    RoleResponse updatePermissions(Actor actor, Long roleId, Set<String> permissionCodes);
}
