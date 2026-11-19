package com.warden.user.service;

import java.util.Set;

import com.warden.common.web.PageResponse;
import com.warden.user.dto.request.CreateUserRequest;
import com.warden.user.dto.request.UpdateUserRequest;
import com.warden.user.dto.response.UserResponse;

/**
 * Managing other people's accounts. Permission checks happen before this service (URL rules +
 * @PreAuthorize); every method here asks {@link UserAccessPolicy} about the scope before writing.
 */
public interface UserManagementService {

    /**
     * One paged query (filters + EXISTS for departments), then roles and departments of the page in
     * batches. Managers are restricted to their own departments by the policy, not by the client.
     */
    PageResponse<UserResponse> list(Actor actor, String query, Long departmentId, int page, int size,
            String sort, String direction);

    UserResponse get(Actor actor, Long id);

    UserResponse create(Actor actor, CreateUserRequest request);

    UserResponse update(Actor actor, Long id, UpdateUserRequest request);

    UserResponse assignDepartments(Actor actor, Long id, Set<Long> departmentIds);

    /** Takes effect on the target's next request: the JWT filter reads roles from the DB. */
    UserResponse assignRoles(Actor actor, Long id, Set<Long> roleIds);

    /** Replaces the direct permissions; like roles, effective from the target's next request. */
    UserResponse assignPermissions(Actor actor, Long id, Set<String> permissionCodes);

    /**
     * Disabling: the JWT filter rejects the target's access tokens at once (TOKEN_REVOKED) and the
     * event makes the auth module revoke their refresh tokens in this transaction.
     */
    UserResponse changeStatus(Actor actor, Long id, boolean enabled);
}
