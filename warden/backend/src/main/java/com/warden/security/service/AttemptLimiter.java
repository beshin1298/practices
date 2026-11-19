package com.warden.security.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.warden.common.error.AppException;
import com.warden.common.error.ErrorCode;

/**
 * Brute-force protection for anything that checks a secret: at most N failures per bucket and
 * subject inside a fixed window that starts at the first failure.
 * <ul>
 *   <li>LOGIN_USER_IP (username + IP): an attacker cannot lock a victim out from another IP.</li>
 *   <li>LOGIN_IP (IP alone, higher limit): stops password spraying, one common password tried on many
 *       usernames, and stops one IP from filling the cache with random usernames to evict lockouts.</li>
 *   <li>PASSWORD_CHECK (user id): someone holding a stolen access token cannot guess the current
 *       password on the change-password form.</li>
 * </ul>
 * In-memory and bounded (Caffeine evicts by size and age); a multi-instance deployment would move
 * the counters to Redis.
 */
@Component
public class AttemptLimiter {

    public enum Bucket { LOGIN_USER_IP, LOGIN_IP, PASSWORD_CHECK }

    private record Key(Bucket bucket, Object subject) {
    }

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    private final Cache<Key, AtomicInteger> failures;
    private final Map<Bucket, Integer> limits;

    public AttemptLimiter(@Value("${app.auth.login-max-failures}") int perUser,
            @Value("${app.auth.login-max-failures-per-ip}") int perIp,
            @Value("${app.auth.login-lockout}") Duration window) {
        this.limits = Map.of(Bucket.LOGIN_USER_IP, perUser, Bucket.LOGIN_IP, perIp, Bucket.PASSWORD_CHECK, perUser);
        this.failures = Caffeine.newBuilder().expireAfterWrite(window).maximumSize(100_000).build();
    }

    /** Checked before the secret, so a correct guess after the limit is still refused. */
    public void checkAllowed(Bucket bucket, Object subject) {
        AtomicInteger count = failures.getIfPresent(new Key(bucket, subject));
        if (count != null && count.get() >= limits.get(bucket)) {
            audit.warn("action=rate_limited bucket={} subject={}", bucket, subject);
            throw new AppException(ErrorCode.TOO_MANY_ATTEMPTS);
        }
    }

    public void recordFailure(Bucket bucket, Object subject) {
        failures.get(new Key(bucket, subject), k -> new AtomicInteger()).incrementAndGet();
    }

    public void reset(Bucket bucket, Object subject) {
        failures.invalidate(new Key(bucket, subject));
    }
}
