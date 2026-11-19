package com.warden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.warden.user.entity.User;

/** FR-2 / FR-4: own profile, department scope for managers, company scope for HR, admin-only roles. */
class UserAccessScopeIntegrationTest extends IntegrationTestSupport {

    @Test
    @DisplayName("FR-2: an employee edits their own name and phone; HR-managed fields cannot be smuggled in")
    void employeeEditsOwnProfileOnly() throws Exception {
        User user = newUser("EMPLOYEE");
        String token = tokenFor(user);

        mvc.perform(put("/api/users/me").header(AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("fullName", "  Trần Thị Bình ", "phone", "0901 234 567",
                                "email", "hacker@evil.test", "jobTitle", "CEO", "roleIds", List.of(1, 2, 3, 4)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Trần Thị Bình"))
                .andExpect(jsonPath("$.phone").value("0901 234 567"))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.jobTitle").doesNotExist())
                .andExpect(jsonPath("$.roles.length()").value(1));
    }

    @Test
    @DisplayName("FR-4: an employee cannot list or edit other people (403 at the URL layer)")
    void employeeCannotManageOthers() throws Exception {
        String token = tokenFor(newUser("EMPLOYEE"));
        User colleague = newUser("EMPLOYEE");

        mvc.perform(get("/api/users").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        updateUser(token, colleague, "Đổi tên", colleague.getEmail()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-4: a manager lists only their own department, whatever filter they send")
    void managerListIsPinnedToOwnDepartment() throws Exception {
        String token = tokenFor(newUser("MANAGER", "SALES"));
        newUser("EMPLOYEE", "SALES");
        newUser("EMPLOYEE", "IT");

        JsonNode page = json.readTree(mvc.perform(get("/api/users").header(AUTHORIZATION, bearer(token)).param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(page.get("totalElements").asInt()).isPositive();
        page.get("content").forEach(user -> assertThat(user.get("departments").findValuesAsText("code")).contains("SALES"));

        mvc.perform(get("/api/users").header(AUTHORIZATION, bearer(token)).param("departmentId", departmentId("IT").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("FR-4: a manager edits same-department staff ranked below, never the email")
    void managerEditsOwnDepartmentOnly() throws Exception {
        String token = tokenFor(newUser("MANAGER", "SALES"));
        User sameDepartment = newUser("EMPLOYEE", "SALES");
        User otherDepartment = newUser("EMPLOYEE", "IT");
        User higherRank = newUser("HR", "SALES");
        User twoDepartments = newUser("EMPLOYEE", "ACC", "SALES");

        updateUser(token, sameDepartment, "Tên mới", sameDepartment.getEmail())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Tên mới"))
                .andExpect(jsonPath("$.jobTitle").value("Trưởng nhóm"));
        updateUser(token, twoDepartments, "Kiêm nhiệm", twoDepartments.getEmail()).andExpect(status().isOk());
        updateUser(token, otherDepartment, "X", otherDepartment.getEmail())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        updateUser(token, higherRank, "X", higherRank.getEmail()).andExpect(status().isForbidden());
        updateUser(token, sameDepartment, "Tên mới", "changed@test.local").andExpect(status().isForbidden());

        // Out of scope reads as "not found", so other departments' ids cannot be probed (403 vs 404).
        mvc.perform(get("/api/users/{id}", otherDepartment.getId()).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mvc.perform(post("/api/users").header(AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(newAccount("by.manager", "EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-4: HR edits anyone up to its rank, including email and departments; not an admin")
    void hrEditsCompanyWide() throws Exception {
        String token = tokenFor(newUser("HR", "HR"));
        User employee = newUser("EMPLOYEE", "IT");
        User admin = newUser("ADMIN", "IT");

        updateUser(token, employee, "Đổi bởi HR", "moved." + employee.getUsername() + "@test.local")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("moved." + employee.getUsername() + "@test.local"));

        mvc.perform(put("/api/users/{id}/departments", employee.getId()).header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("departmentIds", List.of(departmentId("ACC"), departmentId("SALES"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments[*].code").value(containsInAnyOrder("ACC", "SALES")));

        updateUser(token, admin, "X", admin.getEmail())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("FR-4: HR creates accounts (no self-registration) but never an admin")
    void hrCreatesAccounts() throws Exception {
        String token = tokenFor(newUser("HR", "HR"));

        mvc.perform(post("/api/users").header(AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(newAccount("New.Hire", "EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new.hire"))
                .andExpect(jsonPath("$.roles[0].code").value("EMPLOYEE"))
                .andExpect(jsonPath("$.departments[0].code").value("SALES"));
        login("new.hire", "Welc0me123");

        mvc.perform(post("/api/users").header(AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(newAccount("New.Hire", "EMPLOYEE"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        mvc.perform(post("/api/users").header(AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(newAccount("would.be.admin", "ADMIN"))))
                .andExpect(status().isForbidden());

        Map<String, Object> unknownDepartment = new HashMap<>(newAccount("lost.soul", "EMPLOYEE"));
        unknownDepartment.put("departmentIds", List.of(987654));
        mvc.perform(post("/api/users").header(AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(unknownDepartment)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("FR-4: only ADMIN assigns roles; permissions are the union of the roles; never to itself")
    void adminAssignsRoles() throws Exception {
        User admin = newUser("ADMIN", "IT");
        String adminToken = tokenFor(admin);
        User employee = newUser("EMPLOYEE", "SALES");

        mvc.perform(put("/api/users/{id}/roles", employee.getId()).header(AUTHORIZATION, bearer(tokenFor(newUser("HR", "HR"))))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("roleIds", List.of(roleId("MANAGER"))))))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/users/{id}/roles", employee.getId()).header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roleIds", List.of(roleId("MANAGER"), roleId("HR"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[*].code").value(containsInAnyOrder("HR", "MANAGER")));

        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(tokenFor(employee))))
                .andExpect(jsonPath("$.permissions").value(hasItems("USER_READ_DEPARTMENT", "USER_READ_ALL", "USER_CREATE")));

        mvc.perform(put("/api/users/{id}/roles", admin.getId()).header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("roleIds", List.of(roleId("EMPLOYEE"))))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_MODIFICATION"));
    }

    @Test
    @DisplayName("FR-4: HR locks an employee (sessions revoked at once) but not an admin or itself")
    void hrLocksAccounts() throws Exception {
        User hr = newUser("HR", "HR");
        String hrToken = tokenFor(hr);
        User employee = newUser("EMPLOYEE", "IT");
        String employeeToken = tokenFor(employee);

        setEnabled(hrToken, employee, false).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(employeeToken)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));

        setEnabled(hrToken, newUser("ADMIN", "IT"), false).andExpect(status().isForbidden());
        setEnabled(hrToken, hr, false).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_MODIFICATION"));
    }

    private ResultActions updateUser(String token, User target, String fullName, String email) throws Exception {
        return mvc.perform(put("/api/users/{id}", target.getId()).header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("fullName", fullName, "phone", "0912345678", "jobTitle", "Trưởng nhóm", "email", email))));
    }

    private ResultActions setEnabled(String token, User target, boolean enabled) throws Exception {
        return mvc.perform(patch("/api/users/{id}/status", target.getId()).header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("enabled", enabled))));
    }

    private Map<String, Object> newAccount(String username, String roleCode) {
        return Map.of("username", username, "email", username.toLowerCase() + "@test.local", "fullName", "Người Mới",
                "password", "Welc0me123", "roleIds", List.of(roleId(roleCode)), "departmentIds", List.of(departmentId("SALES")));
    }
}
