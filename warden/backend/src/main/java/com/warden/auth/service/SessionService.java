package com.warden.auth.service;

/** A session = short-lived access JWT + rotating refresh token. Starts, refreshes and ends sessions. */
public interface SessionService {

    /**
     * New session (new token family) for a user who has just proven who they are (login, password
     * change). Refuses a disabled account, which also closes the gap between a password change and
     * this call.
     */
    IssuedSession start(Long userId, String userAgent);

    /**
     * Deliberately not @Transactional: {@link RefreshTokenService#rotate} commits on its own, so a
     * rejection thrown here cannot roll back the family revocation done by reuse detection.
     */
    IssuedSession refresh(String rawRefreshToken, String userAgent);

    /**
     * Logout: revokes this device's token family. Access tokens of the session carry it as claim
     * {@code sid}, so the JWT filter rejects them on the next request (TOKEN_REVOKED); other devices
     * keep their own sessions.
     */
    void end(String rawRefreshToken);
}
