package com.warden.security.jwt;

import java.util.UUID;

import io.jsonwebtoken.JwtException;

import com.warden.user.entity.User;

/** Issues and verifies HS256 access tokens. */
public interface JwtService {

    /** {@code sessionId} is the refresh token family of this login; logout revokes it (claim {@code sid}). */
    IssuedToken issue(User user, UUID sessionId);

    /**
     * Verifies signature, issuer and expiry.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException if the token is expired
     * @throws JwtException                        for any other invalid token
     */
    TokenClaims parse(String token);

    record IssuedToken(String value, long expiresInSeconds) {
    }

    record TokenClaims(long userId, int tokenVersion, UUID sessionId) {
    }
}
