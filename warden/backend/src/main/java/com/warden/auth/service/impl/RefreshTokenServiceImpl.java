package com.warden.auth.service.impl;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.warden.auth.config.AuthProperties;
import com.warden.auth.entity.RefreshToken;
import com.warden.auth.repository.RefreshTokenRepository;
import com.warden.auth.service.RefreshTokenService;
import com.warden.auth.service.RotationResult;
import com.warden.auth.service.TokenHasher;
import com.warden.common.error.ErrorCode;

/** Default {@link RefreshTokenService}. */
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository tokens;
    private final AuthProperties properties;

    public RefreshTokenServiceImpl(RefreshTokenRepository tokens, AuthProperties properties) {
        this.tokens = tokens;
        this.properties = properties;
    }

    @Override
    @Transactional
    public String issue(Long userId, UUID familyId, String userAgent) {
        String raw = newRawToken();
        Instant now = Instant.now();
        tokens.save(new RefreshToken(userId, TokenHasher.sha256(raw), familyId, now, now.plus(properties.refreshTokenTtl()), userAgent));
        return raw;
    }

    @Override
    @Transactional
    public RotationResult rotate(String raw, String userAgent) {
        Instant now = Instant.now();
        RefreshToken current = tokens.findByTokenHashForUpdate(TokenHasher.sha256(raw)).orElse(null);

        if (current == null) {
            return new RotationResult.Rejected(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (current.wasRotated()) {
            int revoked = tokens.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected: user={} family={} revoked={}",
                    current.getUserId(), current.getFamilyId(), revoked);
            return new RotationResult.Rejected(ErrorCode.REFRESH_TOKEN_REUSED);
        }
        if (current.isRevoked()) { // logout or password change
            return new RotationResult.Rejected(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (current.isExpired(now)) {
            current.revoke(now, null);
            return new RotationResult.Rejected(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        String nextRaw = newRawToken();
        RefreshToken next = tokens.save(new RefreshToken(current.getUserId(), TokenHasher.sha256(nextRaw), current.getFamilyId(),
                now, now.plus(properties.refreshTokenTtl()), userAgent));
        current.revoke(now, next.getId());
        return new RotationResult.Rotated(current.getUserId(), current.getFamilyId(), nextRaw);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFamilyActive(UUID familyId, Long userId) {
        return tokens.existsByFamilyIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(familyId, userId, Instant.now());
    }

    @Override
    @Transactional
    public void revokeFamilyOf(String raw) {
        tokens.findByTokenHash(TokenHasher.sha256(raw)).ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    @Override
    @Transactional
    public void revokeAllForUser(Long userId) {
        tokens.revokeAllForUser(userId, Instant.now());
    }

    @Override
    @Transactional
    public int purgeExpired() {
        return tokens.deleteExpiredBefore(Instant.now());
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return BASE64_URL.encodeToString(bytes);
    }
}
