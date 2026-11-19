package com.warden.auth.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.warden.auth.dto.response.AuthResponse;
import com.warden.auth.service.IssuedSession;
import com.warden.auth.service.RefreshTokenService;
import com.warden.auth.service.RotationResult;
import com.warden.auth.service.SessionService;
import com.warden.common.error.AppException;
import com.warden.common.error.ErrorCode;
import com.warden.security.jwt.JwtService;
import com.warden.user.entity.User;
import com.warden.user.mapper.UserMapper;
import com.warden.user.repository.UserRepository;

/** Default {@link SessionService}. */
@Service
public class SessionServiceImpl implements SessionService {

    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;
    private final UserRepository users;
    private final UserMapper userMapper;

    public SessionServiceImpl(RefreshTokenService refreshTokens, JwtService jwtService, UserRepository users,
            UserMapper userMapper) {
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.users = users;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public IssuedSession start(Long userId, String userAgent) {
        User user = users.findForAuthenticationById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!user.isEnabled()) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }
        UUID sessionId = UUID.randomUUID(); // = refresh token family id
        String refreshToken = refreshTokens.issue(user.getId(), sessionId, userAgent);
        return new IssuedSession(accessResponse(user, sessionId), refreshToken);
    }

    @Override
    public IssuedSession refresh(String rawRefreshToken, String userAgent) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        return switch (refreshTokens.rotate(rawRefreshToken, userAgent)) {
            case RotationResult.Rejected rejected -> throw new AppException(rejected.reason());
            case RotationResult.Rotated rotated -> {
                // Roles, permissions, departments fetched in one query: this method runs outside a transaction.
                User user = users.findForAuthenticationById(rotated.userId()).filter(User::isEnabled).orElse(null);
                if (user == null) { // defence in depth: disabling already revoked the tokens
                    refreshTokens.revokeAllForUser(rotated.userId());
                    throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
                }
                yield new IssuedSession(accessResponse(user, rotated.familyId()), rotated.refreshToken());
            }
        };
    }

    @Override
    public void end(String rawRefreshToken) {
        if (StringUtils.hasText(rawRefreshToken)) {
            refreshTokens.revokeFamilyOf(rawRefreshToken);
        }
    }

    private AuthResponse accessResponse(User user, UUID sessionId) {
        JwtService.IssuedToken token = jwtService.issue(user, sessionId);
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), userMapper.toCurrentUser(user));
    }
}
