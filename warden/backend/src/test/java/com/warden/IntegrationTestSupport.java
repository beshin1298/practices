package com.warden;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.warden.department.repository.DepartmentRepository;
import com.warden.role.repository.RoleRepository;
import com.warden.user.entity.User;
import com.warden.user.repository.UserRepository;

/** Full application + MockMvc + PostgreSQL container. Each test creates its own users. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "Passw0rd!";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected RoleRepository roles;

    @Autowired
    protected DepartmentRepository departments;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** A user with one role, in the IT department. */
    protected User newUser(String roleCode) {
        return newUser(roleCode, "IT");
    }

    protected User newUser(String roleCode, String... departmentCodes) {
        return newUser(List.of(roleCode), departmentCodes);
    }

    protected User newUser(List<String> roleCodes, String... departmentCodes) {
        String username = roleCodes.getFirst().toLowerCase() + "-" + SEQUENCE.incrementAndGet() + "-" + (System.nanoTime() % 100_000);
        User user = new User(username, username + "@test.local", "Test " + username, passwordEncoder.encode(PASSWORD));
        user.replaceRoles(roleCodes.stream().map(code -> roles.findByCode(code).orElseThrow()).toList());
        user.replaceDepartments(Arrays.stream(departmentCodes).map(code -> departments.findByCode(code).orElseThrow()).toList());
        return users.save(user);
    }

    protected Long roleId(String code) {
        return roles.findByCode(code).orElseThrow().getId();
    }

    protected Long departmentId(String code) {
        return departments.findByCode(code).orElseThrow().getId();
    }

    protected String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return accessToken(result);
    }

    protected String tokenFor(User user) throws Exception {
        return login(user.getUsername(), PASSWORD);
    }

    protected String accessToken(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    protected String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
