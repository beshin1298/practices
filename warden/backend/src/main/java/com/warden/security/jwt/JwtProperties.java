package com.warden.security.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret     base64 HMAC key, at least 32 bytes (HS256)
 * @param expiration access token lifetime, e.g. PT60M
 * @param issuer     value of the "iss" claim; tokens with another issuer are rejected
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiration, String issuer) {
}
