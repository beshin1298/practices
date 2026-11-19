package com.warden.user.service;

import com.warden.user.dto.request.ChangePasswordRequest;
import com.warden.user.dto.request.UpdateOwnProfileRequest;
import com.warden.user.dto.response.CurrentUserResponse;

/**
 * Operations a user performs on their own account (USER_READ_OWN / USER_UPDATE_OWN). The user id
 * always comes from the token. Knows nothing about tokens or sessions: it announces security
 * changes with an event.
 */
public interface AccountService {

    CurrentUserResponse getProfile(Long userId);

    /** Only the fields an employee owns; email, job title, departments and roles belong to HR. */
    CurrentUserResponse updateProfile(Long userId, UpdateOwnProfileRequest request);

    /**
     * Verifies the current password, stores the new hash and bumps token_version (every access token
     * issued before stops working). The event makes the auth module revoke every refresh token in
     * this same transaction. The caller then starts a fresh session for the current device.
     */
    void changePassword(Long userId, ChangePasswordRequest request);
}
