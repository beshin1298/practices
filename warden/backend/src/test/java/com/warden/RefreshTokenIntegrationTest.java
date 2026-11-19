package com.warden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.warden.auth.controller.RefreshTokenCookies;
import com.warden.auth.repository.RefreshTokenRepository;
import com.warden.auth.service.TokenHasher;
import com.warden.security.jwt.JwtProperties;
import com.warden.user.entity.User;

/** FR-5 (sessions: refresh token, logout, brute-force protection) from the requirements doc. */
class RefreshTokenIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    @DisplayName("FR-5: login sets an HttpOnly, SameSite=Strict cookie and the DB stores only its SHA-256")
    void loginSetsHardenedCookie() throws Exception {
        MvcResult login = loginAs(newUser("EMPLOYEE"));
        MockCookie cookie = refreshCookie(login);

        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(7 * 24 * 3600);
        assertThat(login.getResponse().getContentAsString()).doesNotContain(cookie.getValue());
        assertThat(refreshTokens.findByTokenHash(cookie.getValue())).isEmpty();
        assertThat(refreshTokens.findByTokenHash(TokenHasher.sha256(cookie.getValue()))).isPresent();
    }

    @Test
    @DisplayName("FR-5: refresh returns a working access token and rotates the refresh token")
    void refreshRotatesToken() throws Exception {
        MockCookie first = refreshCookie(loginAs(newUser("EMPLOYEE")));

        MvcResult refreshed = refresh(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        MockCookie second = refreshCookie(refreshed);

        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(accessToken(refreshed))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-5: replaying a rotated token revokes the whole family (reuse detection)")
    void reuseRevokesFamily() throws Exception {
        MockCookie first = refreshCookie(loginAs(newUser("EMPLOYEE")));
        MockCookie second = refreshCookie(refresh(first).andExpect(status().isOk()).andReturn());

        refresh(first) // an attacker replays the stolen, already-rotated token
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
        refresh(second) // the legitimate successor dies with it
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("FR-5: refresh without a cookie or with an expired token is rejected and the cookie cleared")
    void missingOrExpiredTokenRejected() throws Exception {
        MvcResult missing = mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"))
                .andReturn();
        assertThat(refreshCookie(missing).getMaxAge()).isZero();

        MockCookie cookie = refreshCookie(loginAs(newUser("EMPLOYEE")));
        jdbc.update("update refresh_tokens set created_at = now() - interval '9 days', expires_at = now() - interval '2 days' "
                + "where token_hash = ?", TokenHasher.sha256(cookie.getValue()));
        refresh(cookie)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("FR-5: logout revokes the session and clears the cookie")
    void logoutRevokesSession() throws Exception {
        MockCookie cookie = refreshCookie(loginAs(newUser("EMPLOYEE")));

        MvcResult logout = mvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(refreshCookie(logout).getMaxAge()).isZero();

        refresh(cookie)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("FR-5: logout rejects that session's access token at once; other devices stay signed in")
    void logoutRevokesAccessTokenOfThatSessionOnly() throws Exception {
        User user = newUser("EMPLOYEE");
        MvcResult laptop = loginAs(user);
        MvcResult phone = loginAs(user);

        mvc.perform(post("/api/auth/logout").cookie(refreshCookie(laptop))).andExpect(status().isNoContent());

        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(accessToken(laptop))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(accessToken(phone))))
                .andExpect(status().isOk());
        refresh(refreshCookie(phone)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-5: rotation keeps the session's access tokens; reuse detection rejects them all")
    void reuseRevokesAccessTokensOfFamily() throws Exception {
        MvcResult login = loginAs(newUser("EMPLOYEE"));
        MockCookie first = refreshCookie(login);
        MvcResult refreshed = refresh(first).andExpect(status().isOk()).andReturn();

        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(accessToken(login))))
                .andExpect(status().isOk());

        refresh(first).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));

        for (MvcResult issued : new MvcResult[] {login, refreshed}) {
            mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(accessToken(issued))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
        }
    }

    @Test
    @DisplayName("FR-5: a correctly signed access token without a session id (sid) is rejected")
    void accessTokenWithoutSessionIdRejected() throws Exception {
        User user = newUser("EMPLOYEE");
        String token = Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(String.valueOf(user.getId()))
                .claim("ver", user.getTokenVersion())
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret())), Jwts.SIG.HS256)
                .compact();

        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("FR-3/FR-5: changing the password ends every other session; this device gets a new one")
    void passwordChangeRevokesOtherSessions() throws Exception {
        User user = newUser("EMPLOYEE");
        MvcResult laptop = loginAs(user);
        MockCookie phone = refreshCookie(loginAs(user));

        MvcResult changed = mvc.perform(put("/api/users/me/password")
                        .header(AUTHORIZATION, bearer(accessToken(laptop)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", PASSWORD, "newPassword", "N3wPassword"))))
                .andExpect(status().isOk())
                .andReturn();

        refresh(phone).andExpect(status().isUnauthorized());
        refresh(refreshCookie(laptop)).andExpect(status().isUnauthorized());
        refresh(refreshCookie(changed)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-4/FR-5: disabling a user revokes their refresh tokens")
    void disablingRevokesRefreshTokens() throws Exception {
        String adminToken = tokenFor(newUser("HR"));
        User user = newUser("EMPLOYEE");
        MockCookie cookie = refreshCookie(loginAs(user));

        mvc.perform(patch("/api/users/{id}/status", user.getId()).header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("enabled", false))))
                .andExpect(status().isOk());

        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR-5: after 5 failed logins even the correct password gets 429 for a while")
    void bruteForceIsThrottled() throws Exception {
        User user = newUser("EMPLOYEE");
        for (int i = 0; i < 5; i++) {
            postLogin(user.getUsername(), "Wrong1234").andExpect(status().isUnauthorized());
        }
        postLogin(user.getUsername(), PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    private MvcResult loginAs(User user) throws Exception {
        return postLogin(user.getUsername(), PASSWORD).andExpect(status().isOk()).andReturn();
    }

    private ResultActions postLogin(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", username, "password", password))));
    }

    private ResultActions refresh(Cookie cookie) throws Exception {
        return mvc.perform(post("/api/auth/refresh").cookie(cookie));
    }

    private static MockCookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(RefreshTokenCookies.NAME);
        assertThat(cookie).as("Set-Cookie %s", RefreshTokenCookies.NAME).isNotNull();
        return (MockCookie) cookie;
    }
}
