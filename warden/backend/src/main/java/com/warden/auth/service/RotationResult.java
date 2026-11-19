package com.warden.auth.service;

import java.util.UUID;

import com.warden.common.error.ErrorCode;

/**
 * Outcome of {@link RefreshTokenService#rotate}. A rejection is returned, not thrown: throwing inside
 * the transaction would roll back the family revocation that reuse detection just performed.
 */
public sealed interface RotationResult {

    /** {@code familyId} is the session id the new access token carries as claim {@code sid}. */
    record Rotated(Long userId, UUID familyId, String refreshToken) implements RotationResult {

        @Override
        public String toString() {
            return "Rotated[userId=" + userId + ", familyId=" + familyId + ", refreshToken=***]";
        }
    }

    record Rejected(ErrorCode reason) implements RotationResult {
    }
}
