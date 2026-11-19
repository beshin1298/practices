package com.warden.auth.service;

import java.util.UUID;

/** Life cycle of refresh tokens: issue, rotate with reuse detection, revoke, purge. */
public interface RefreshTokenService {

    /** Creates a token in {@code familyId} and returns the raw value (never stored). */
    String issue(Long userId, UUID familyId, String userAgent);

    /**
     * Exchanges a valid token for a new one in the same family. Replaying a token that was already
     * rotated means someone copied it: the whole family is revoked, logging out attacker and victim.
     */
    RotationResult rotate(String raw, String userAgent);

    /**
     * A family is one login session of one user: it is live while one of its tokens is neither revoked nor expired. Logout, reuse
     * detection, password change and disabling all revoke the whole family, so access tokens of that
     * session die at once too.
     */
    boolean isFamilyActive(UUID familyId, Long userId);

    /** Logout: ends the session this token belongs to (all tokens of its family). */
    void revokeFamilyOf(String raw);

    /** Password changed or account disabled: every session of the user ends. */
    void revokeAllForUser(Long userId);

    int purgeExpired();
}
