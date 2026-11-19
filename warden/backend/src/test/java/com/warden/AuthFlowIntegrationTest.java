package com.warden;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.warden.user.entity.User;

/** FR-1 (login) and FR-3 (change password) from the requirements doc. */
class AuthFlowIntegrationTest extends IntegrationTestSupport {

    @Test
    @DisplayName("FR-1: correct credentials return a Bearer token and the profile, never the hash")
    void loginReturnsTokenAndProfile() throws Exception {
        User user = newUser("EMPLOYEE");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "  " + user.getUsername().toUpperCase() + " ", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value(user.getUsername()))
                .andExpect(jsonPath("$.user.lastLoginAt").isNotEmpty())
                .andExpect(jsonPath("$.user.roles[0].code").value("EMPLOYEE"))
                .andExpect(jsonPath("$.user.departments[0].code").value("IT"))
                .andExpect(jsonPath("$.user.permissions").value(org.hamcrest.Matchers.containsInAnyOrder("USER_READ_OWN", "USER_UPDATE_OWN")))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.tokenVersion").doesNotExist());
    }

    @Test
    @DisplayName("FR-1: wrong password and unknown username get the same 401")
    void wrongPasswordAndUnknownUserLookTheSame() throws Exception {
        User user = newUser("EMPLOYEE");
        List<Map<String, String>> attempts = List.of(
                Map.of("username", user.getUsername(), "password", "Wrong1234"),
                Map.of("username", "no-such-user", "password", PASSWORD));

        for (Map<String, String> attempt : attempts) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(attempt)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.detail").value("Sai tên đăng nhập hoặc mật khẩu"));
        }
    }

    @Test
    @DisplayName("FR-1: a disabled account is only revealed after the password is correct")
    void disabledAccountIsRejectedAfterPasswordCheck() throws Exception {
        User user = newUser("EMPLOYEE");
        user.setEnabled(false);
        users.save(user);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", user.getUsername(), "password", PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", user.getUsername(), "password", "Wrong1234"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("FR-1: blank fields return 400 with per-field errors")
    void loginValidatesInput() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "", "password", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("Protected endpoints answer 401 with a reason code")
    void protectedEndpointRequiresValidToken() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/users/me").header(AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("FR-3: changing the password revokes old tokens and returns a working new one")
    void changePasswordRevokesOldTokens() throws Exception {
        User user = newUser("EMPLOYEE");
        String oldToken = tokenFor(user);
        String newPassword = "N3wPassword";

        MvcResult result = mvc.perform(put("/api/users/me/password")
                        .header(AUTHORIZATION, bearer(oldToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", PASSWORD, "newPassword", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.passwordChangedAt").isNotEmpty())
                .andReturn();
        String newToken = accessToken(result);

        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(oldToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(newToken)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", user.getUsername(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
        login(user.getUsername(), newPassword);
    }

    @Test
    @DisplayName("FR-3: wrong current password, weak, reused or too long new password are rejected")
    void changePasswordValidatesInput() throws Exception {
        User user = newUser("EMPLOYEE");
        String token = tokenFor(user);

        changePassword(token, "Wrong1234", "N3wPassword")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"));

        changePassword(token, PASSWORD, "onlyletters")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.newPassword").value("Mật khẩu phải có cả chữ và số"));

        changePassword(token, PASSWORD, "a1" + "x".repeat(71))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").value("Mật khẩu tối đa 72 byte"));

        changePassword(token, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_REUSED"));

        // None of the failed attempts revoked the session.
        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(token))).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(String token, String current, String next)
            throws Exception {
        return mvc.perform(put("/api/users/me/password")
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("currentPassword", current, "newPassword", next))));
    }
}
