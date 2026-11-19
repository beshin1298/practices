package com.warden.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param refreshTokenTtl  lifetime of one refresh token; each refresh issues a new one (sliding window)
 * @param cookieSecure     adds the Secure flag to the refresh cookie (must be true behind HTTPS)
 * @param loginMaxFailures failed logins allowed per username + IP inside {@code loginLockout}
 * @param loginLockout     window after the first failure during which failures are counted
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(Duration refreshTokenTtl, boolean cookieSecure, int loginMaxFailures,
        Duration loginLockout) {
}
