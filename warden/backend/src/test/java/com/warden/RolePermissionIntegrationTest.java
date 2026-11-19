package com.warden;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.warden.user.entity.User;

/** Screen 5 (editing the role x permission matrix) and direct permissions ("quyền lẻ") on screen 4. */
class RolePermissionIntegrationTest extends IntegrationTestSupport {

    @Test
    @DisplayName("VT-05: Admin changes a lower role; holders get it on their next request, without logging in again")
    void adminEditsLowerRoleEffectiveImmediately() throws Exception {
        String admin = tokenFor(newUser("ADMIN"));
        String employee = tokenFor(newUser("EMPLOYEE"));
        Long employeeRole = roleId("EMPLOYEE");
        List<String> original = roles.findWithPermissionsById(employeeRole).orElseThrow().permissionCodes();
        listUsers(employee).andExpect(status().isForbidden());

        try {
            Set<String> widened = new TreeSet<>(original);
            widened.add("USER_READ_ALL");
            replace("/api/roles/{id}/permissions", employeeRole, widened, admin)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.permissions", hasItem("USER_READ_ALL")));
            listUsers(employee).andExpect(status().isOk());
        } finally { // roles are shared by every test: always put the seed back
            replace("/api/roles/{id}/permissions", employeeRole, original, admin).andExpect(status().isOk());
        }
        listUsers(employee).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("VT-06: the top role (ADMIN) is locked; an unknown permission code is rejected")
    void adminRoleLockedAndUnknownCodeRejected() throws Exception {
        String admin = tokenFor(newUser("ADMIN"));

        replace("/api/roles/{id}/permissions", roleId("ADMIN"), Set.of(), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_LOCKED"));
        replace("/api/roles/{id}/permissions", roleId("EMPLOYEE"), Set.of("NOT_A_PERMISSION"), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERMISSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("NS-quyền lẻ: a direct permission applies to that one person only, at once, and can be taken back")
    void directPermissionAppliesToOnePerson() throws Exception {
        String admin = tokenFor(newUser("ADMIN"));
        User granted = newUser("EMPLOYEE");
        String grantedToken = tokenFor(granted);
        String colleague = tokenFor(newUser("EMPLOYEE"));

        replace("/api/users/{id}/permissions", granted.getId(), Set.of("USER_READ_ALL"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directPermissions", contains("USER_READ_ALL")))
                .andExpect(jsonPath("$.roles[0].code").value("EMPLOYEE"));
        listUsers(grantedToken).andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(grantedToken)))
                .andExpect(jsonPath("$.permissions", hasItem("USER_READ_ALL")));
        listUsers(colleague).andExpect(status().isForbidden());

        replace("/api/users/{id}/permissions", granted.getId(), Set.of(), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directPermissions", empty()));
        listUsers(grantedToken).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("NS-quyền lẻ: not to yourself, not to someone ranked above you, only permissions you hold")
    void directPermissionLimits() throws Exception {
        User adminUser = newUser("ADMIN");
        String admin = tokenFor(adminUser);
        replace("/api/users/{id}/permissions", adminUser.getId(), Set.of("USER_READ_ALL"), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_MODIFICATION"));

        // An employee who was handed USER_ASSIGN_ROLE can pass on only what they hold, only to rank <= 0.
        User delegate = newUser("EMPLOYEE");
        replace("/api/users/{id}/permissions", delegate.getId(), Set.of("USER_ASSIGN_ROLE"), admin).andExpect(status().isOk());
        String delegateToken = tokenFor(delegate);

        replace("/api/users/{id}/permissions", newUser("EMPLOYEE").getId(), Set.of("USER_DISABLE"), delegateToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        replace("/api/users/{id}/permissions", newUser("MANAGER").getId(), Set.of("USER_ASSIGN_ROLE"), delegateToken)
                .andExpect(status().isForbidden());
        replace("/api/users/{id}/permissions", newUser("EMPLOYEE").getId(), Set.of("USER_ASSIGN_ROLE"), delegateToken)
                .andExpect(status().isOk());
    }

    private ResultActions listUsers(String token) throws Exception {
        return mvc.perform(get("/api/users").header(AUTHORIZATION, bearer(token)));
    }

    private ResultActions replace(String path, Long id, Collection<String> permissions, String token) throws Exception {
        return mvc.perform(put(path, id).header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("permissions", permissions))));
    }
}
