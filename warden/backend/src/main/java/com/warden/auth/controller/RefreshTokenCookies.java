package com.warden.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.warden.auth.config.AuthProperties;
import com.warden.auth.dto.response.AuthResponse;
import com.warden.auth.service.IssuedSession;

/**
 * HTTP representation of the refresh token (kept out of the services: SRP).
 * <ul>
 *   <li>HttpOnly: JavaScript, and therefore XSS, cannot read it.</li>
 *   <li>SameSite=Strict: never sent on cross-site requests, which is what protects the cookie-based
 *       /refresh and /logout endpoints against CSRF (CSRF tokens are disabled app-wide).</li>
 *   <li>Path=/api/auth: not sent with ordinary API calls.</li>
 * </ul>
 */
@Component
public class RefreshTokenCookies {

    public static final String NAME = "warden_refresh";
    static final String PATH = "/api/auth";

    private final AuthProperties properties;

    public RefreshTokenCookies(AuthProperties properties) {
        this.properties = properties;
    }

    /** 200 with the session body and the refresh cookie. */
    public ResponseEntity<AuthResponse> respond(IssuedSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, build(session.refreshToken(), properties.refreshTokenTtl().toSeconds()).toString())
                .body(session.body());
    }

    public ResponseCookie cleared() {
        return build("", 0);
    }

    private ResponseCookie build(String value, long maxAgeSeconds) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }
}
