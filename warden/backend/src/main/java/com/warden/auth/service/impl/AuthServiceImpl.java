package com.warden.auth.service.impl;

import static com.warden.security.service.AttemptLimiter.Bucket.LOGIN_IP;
import static com.warden.security.service.AttemptLimiter.Bucket.LOGIN_USER_IP;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.warden.auth.dto.request.LoginRequest;
import com.warden.auth.service.AuthService;
import com.warden.auth.service.IssuedSession;
import com.warden.auth.service.SessionService;
import com.warden.common.error.AppException;
import com.warden.common.error.ErrorCode;
import com.warden.common.util.Normalizer;
import com.warden.common.validation.PasswordPolicyValidator;
import com.warden.security.service.AttemptLimiter;
import com.warden.user.entity.User;
import com.warden.user.repository.UserRepository;

/**
 * Default {@link AuthService}. Deliberately not @Transactional: BCrypt takes ~100 ms, and holding a
 * pooled DB connection that long per login would let a handful of parallel logins starve the pool.
 * Reads and writes below each run in their own short transaction.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    /** Rate-limit subject: one username tried from one client IP. */
    private record UsernameAndIp(String username, String ip) {
    }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final AttemptLimiter limiter;

    /**
     * Compared against when the username does not exist, so a wrong username costs the same
     * BCrypt time as a wrong password and response time does not reveal which usernames exist.
     * Computed once at startup, not per request.
     */
    private final String dummyHash;

    public AuthServiceImpl(UserRepository users, PasswordEncoder passwordEncoder, SessionService sessions,
            AttemptLimiter limiter) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.limiter = limiter;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public IssuedSession login(LoginRequest request, String clientIp, String userAgent) {
        String username = Normalizer.key(request.username());
        UsernameAndIp attempt = new UsernameAndIp(username, clientIp);
        limiter.checkAllowed(LOGIN_IP, clientIp);
        limiter.checkAllowed(LOGIN_USER_IP, attempt);

        Optional<User> found = users.findByUsername(username); // short read-only transaction
        boolean passwordMatches = PasswordPolicyValidator.fitsBcrypt(request.password())
                && passwordEncoder.matches(request.password(), found.map(User::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordMatches) {
            limiter.recordFailure(LOGIN_USER_IP, attempt);
            limiter.recordFailure(LOGIN_IP, clientIp);
            audit.warn("action=login_failed username={} ip={}", username, clientIp);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        limiter.reset(LOGIN_USER_IP, attempt);

        User user = found.get();
        // Checked only after the password is correct, so strangers cannot probe for locked accounts.
        if (!user.isEnabled()) {
            audit.warn("action=login_disabled userId={} ip={}", user.getId(), clientIp);
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }

        users.updateLastLoginAt(user.getId(), Instant.now());
        audit.info("action=login userId={} ip={}", user.getId(), clientIp);
        return sessions.start(user.getId(), userAgent);
    }
}
