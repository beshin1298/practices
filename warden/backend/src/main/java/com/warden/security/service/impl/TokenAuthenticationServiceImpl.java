package com.warden.security.service.impl;

import java.util.Set;
import java.util.stream.Collectors;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.warden.auth.service.RefreshTokenService;
import com.warden.common.error.ErrorCode;
import com.warden.role.entity.Role;
import com.warden.security.AuthUser;
import com.warden.security.jwt.JwtService;
import com.warden.security.service.TokenAuthenticationService;
import com.warden.security.service.TokenCheck;
import com.warden.user.entity.User;
import com.warden.user.repository.UserRepository;

/** Default {@link TokenAuthenticationService}. */
@Service
@Transactional(readOnly = true)
public class TokenAuthenticationServiceImpl implements TokenAuthenticationService {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final UserRepository users;

    public TokenAuthenticationServiceImpl(JwtService jwtService, RefreshTokenService refreshTokens, UserRepository users) {
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.users = users;
    }

    @Override
    public TokenCheck authenticate(String token) {
        JwtService.TokenClaims claims;
        try {
            claims = jwtService.parse(token);
        } catch (ExpiredJwtException ex) {
            return new TokenCheck.Rejected(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            return new TokenCheck.Rejected(ErrorCode.TOKEN_INVALID);
        }

        // Logged out, refresh token reused, password changed or disabled: the session's family is revoked.
        // Checked first: an index-only lookup, and a dead session skips the user query below.
        if (!refreshTokens.isFamilyActive(claims.sessionId(), claims.userId())) {
            return new TokenCheck.Rejected(ErrorCode.TOKEN_REVOKED);
        }
        // One query: user + roles + permissions + departments (entity graph).
        User user = users.findForAuthenticationById(claims.userId()).orElse(null);
        // Deleted, disabled, or password changed since the token was issued.
        if (user == null || !user.isEnabled() || user.getTokenVersion() != claims.tokenVersion()) {
            return new TokenCheck.Rejected(ErrorCode.TOKEN_REVOKED);
        }
        return new TokenCheck.Accepted(toPrincipal(user));
    }

    private static AuthUser toPrincipal(User user) {
        Set<String> roles = user.getRoles().stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet());
        return new AuthUser(user.getId(), user.getUsername(), roles, Set.copyOf(user.permissionCodes()),
                user.departmentIds(), user.rank());
    }
}
