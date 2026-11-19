package com.warden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.warden.common.error.AppException;
import com.warden.role.constant.Permissions;
import com.warden.security.AuthUser;
import com.warden.security.jwt.JwtProperties;
import com.warden.user.entity.User;
import com.warden.user.service.UserManagementService;

/** Fixes from the security review: rate limits, session binding, privilege escalation, input edge cases. */
class SecurityHardeningIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserManagementService userManagement;

    @Test
    @DisplayName("A sid copied from my own session into a token for another user is rejected")
    void sessionIdIsBoundToItsUser() throws Exception {
        String employeeToken = tokenFor(newUser("EMPLOYEE"));
        String sid = json.readTree(Base64.getUrlDecoder().decode(employeeToken.split("\\.")[1])).get("sid").asText();
        User admin = newUser("ADMIN");

        String forged = Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(String.valueOf(admin.getId()))
                .claim("ver", admin.getTokenVersion())
                .claim("sid", sid)
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret())), Jwts.SIG.HS256)
                .compact();

        mvc.perform(get("/api/users").header(AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    @Test
    @DisplayName("DMK: guessing the current password is limited; even the right one is refused after 5 misses")
    void currentPasswordGuessingIsThrottled() throws Exception {
        String token = tokenFor(newUser("EMPLOYEE"));
        for (int i = 0; i < 5; i++) {
            changePassword(token, "Wrong1234").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"));
        }
        changePassword(token, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    @DisplayName("DN: one IP spraying many usernames is blocked for every username; other IPs are not")
    void passwordSprayingFromOneIpIsThrottled() throws Exception {
        for (int i = 0; i < 30; i++) {
            loginFrom("10.20.30.40", "nobody-" + i, "Wrong1234").andExpect(status().isUnauthorized());
        }
        User real = newUser("EMPLOYEE");
        loginFrom("10.20.30.40", real.getUsername(), PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        loginFrom("10.20.30.41", real.getUsername(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("NS: HR cannot create an account whose role carries a permission HR does not hold")
    void creatingAccountsCannotEscalatePermissions() throws Exception {
        String admin = tokenFor(newUser("ADMIN"));
        String hr = tokenFor(newUser("HR"));
        Long managerRole = roleId("MANAGER");
        List<String> original = roles.findWithPermissionsById(managerRole).orElseThrow().permissionCodes();

        // A wider scope covers a narrower one: HR (USER_*_ALL) may create managers (USER_*_DEPARTMENT).
        createManager(hr, managerRole).andExpect(status().isCreated());
        try {
            Set<String> widened = new TreeSet<>(original);
            widened.add(Permissions.USER_ASSIGN_ROLE);
            mvc.perform(put("/api/roles/{id}/permissions", managerRole).header(AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("permissions", widened))))
                    .andExpect(status().isOk());
            createManager(hr, managerRole)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        } finally { // roles are shared by every test: always put the seed back
            mvc.perform(put("/api/roles/{id}/permissions", managerRole).header(AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("permissions", original))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @Transactional // rolled back: the other top-ranked users are disabled only inside this test
    @DisplayName("NS: the last enabled top-ranked user can be neither disabled nor demoted")
    void lastTopRankedUserIsKept() {
        User lastAdmin = newUser("ADMIN");
        jdbc.update("update users set enabled = false where id <> ? and id in "
                + "(select ur.user_id from user_roles ur join roles r on r.id = ur.role_id where r.rank = 3)", lastAdmin.getId());
        // Someone else with full rights (rank 3), e.g. a second admin whose request raced this one.
        AuthUser otherAdmin = new AuthUser(-1L, "racing-admin", Set.of("ADMIN"), Permissions.ALL, Set.of(), 3);

        assertThatThrownBy(() -> userManagement.changeStatus(otherAdmin, lastAdmin.getId(), false))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.code().name()).isEqualTo("LAST_TOP_USER"));
        assertThatThrownBy(() -> userManagement.assignRoles(otherAdmin, lastAdmin.getId(), Set.of(roleId("EMPLOYEE"))))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.code().name()).isEqualTo("LAST_TOP_USER"));
    }

    @Test
    @DisplayName("NS: % and _ in the search box are literal characters, and the search term is capped")
    void searchTreatsWildcardsLiterally() throws Exception {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        users.save(new User("wq_" + suffix, "wq_" + suffix + "@test.local", "Underscore", passwordEncoder.encode(PASSWORD)));
        users.save(new User("wqx" + suffix, "wqx" + suffix + "@test.local", "Letter", passwordEncoder.encode(PASSWORD)));
        String hr = tokenFor(newUser("HR"));

        mvc.perform(get("/api/users").param("q", "wq_" + suffix).header(AUTHORIZATION, bearer(hr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("wq_" + suffix));
        mvc.perform(get("/api/users").param("q", "x".repeat(101)).header(AUTHORIZATION, bearer(hr)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Input edge cases: [null] in an id list and a phone made of '+' and spaces are 400")
    void malformedListsAndPhonesAreRejected() throws Exception {
        String admin = tokenFor(newUser("ADMIN"));
        mvc.perform(put("/api/users/{id}/roles", newUser("EMPLOYEE").getId()).header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String employee = tokenFor(newUser("EMPLOYEE"));
        for (String phone : List.of("+        ", "+123456789012345678901")) {
            mvc.perform(put("/api/users/me").header(AUTHORIZATION, bearer(employee))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("fullName", "Tên", "phone", phone))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.phone").exists());
        }
    }

    @Test
    @DisplayName("401 answers carry WWW-Authenticate: Bearer (RFC 6750)")
    void unauthorizedNamesTheScheme() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"warden\""));
        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer("not-a-jwt")))
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"warden\", error=\"invalid_token\""));
    }

    private ResultActions changePassword(String token, String current) throws Exception {
        return mvc.perform(put("/api/users/me/password").header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("currentPassword", current, "newPassword", "N3wPassword"))));
    }

    private ResultActions loginFrom(String ip, String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", username, "password", password))));
    }

    private ResultActions createManager(String token, Long managerRole) throws Exception {
        String username = "mgr" + System.nanoTime() % 10_000_000;
        return mvc.perform(post("/api/users").header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", username, "email", username + "@test.local", "fullName", "Quản lý mới",
                        "password", PASSWORD, "roleIds", List.of(managerRole), "departmentIds", List.of(departmentId("SALES"))))));
    }
}
