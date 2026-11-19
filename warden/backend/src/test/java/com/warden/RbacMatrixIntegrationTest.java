package com.warden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import com.warden.role.constant.Permissions;
import com.warden.role.entity.Role;
import com.warden.user.entity.User;

/**
 * Spec-driven authorization test over docs/rbac-matrix.md:
 * <ol>
 *   <li>the Roles × Permissions table must equal the role_permissions rows in the database;</li>
 *   <li>every API row is called anonymously and as each role; a role is let through exactly when it
 *       holds one of the row's permissions.</li>
 * </ol>
 * Adding a matrix row adds test cases. "Blocked" means stopped by the security layer (401 with an
 * access-token code, or 403 ACCESS_DENIED); an endpoint's own answer (400 for the "{}" body, 401
 * REFRESH_TOKEN_INVALID from /refresh without a cookie) still proves authorization let it through.
 * The target of {id} paths is an EMPLOYEE in the callers' department, inside every allowed scope.
 */
class RbacMatrixIntegrationTest extends IntegrationTestSupport {

    private static final Path MATRIX = Path.of("..", "docs", "rbac-matrix.md");
    private static final Set<String> AUTHENTICATION_CODES =
            Set.of("UNAUTHENTICATED", "TOKEN_EXPIRED", "TOKEN_INVALID", "TOKEN_REVOKED");

    private record Outcome(int status, String code) {

        boolean blockedAsAnonymous() {
            return status == 401 && AUTHENTICATION_CODES.contains(code);
        }

        boolean blockedByPermission() {
            return status == 403 && "ACCESS_DENIED".equals(code);
        }

        boolean allowed() {
            return !blockedAsAnonymous() && !blockedByPermission();
        }
    }

    @Test
    @Transactional
    @DisplayName("Roles × Permissions in the matrix == role_permissions in the database")
    void rolePermissionsMatchTheMatrix() throws IOException {
        Map<String, Set<String>> inDatabase = roles.findAllByOrderByRankAsc().stream()
                .collect(Collectors.toMap(Role::getCode, role -> new TreeSet<>(role.permissionCodes())));
        assertThat(inDatabase).isEqualTo(specRolePermissions());
        assertThat(specRolePermissions().values().stream().flatMap(Set::stream))
                .as("every permission in the matrix is declared in Permissions.java")
                .allMatch(Permissions.ALL::contains);
    }

    static Stream<Arguments> apiRows() throws IOException {
        return table("Method").stream()
                .map(cells -> Arguments.of(cells.get(0).toUpperCase(), cells.get(1), cells.get(2).toUpperCase()));
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @MethodSource("apiRows")
    void endpointEnforcesItsAccessLevel(String method, String path, String access) throws Exception {
        User target = newUser("EMPLOYEE");
        String url = path.replace("{id}", String.valueOf(target.getId()));

        Outcome anonymous = call(method, url, null);
        if (access.equals("PUBLIC")) {
            assertThat(anonymous.allowed()).as("anonymous %s", anonymous).isTrue();
            return;
        }
        assertThat(anonymous.blockedAsAnonymous()).as("anonymous %s", anonymous).isTrue();

        Set<String> required = access.equals("AUTHENTICATED") ? null : permissionsIn(access);
        for (Map.Entry<String, Set<String>> role : specRolePermissions().entrySet()) {
            Outcome outcome = call(method, url, tokenFor(newUser(role.getKey())));
            boolean expected = required == null || !Collections.disjoint(role.getValue(), required);
            if (expected) {
                assertThat(outcome.allowed()).as("%s %s", role.getKey(), outcome).isTrue();
            } else {
                assertThat(outcome.blockedByPermission()).as("%s %s", role.getKey(), outcome).isTrue();
            }
        }
    }

    private Outcome call(String method, String url, String token) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), url)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (token != null) {
            request.header(AUTHORIZATION, bearer(token));
        }
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        String body = response.getContentAsString();
        String code = body.isBlank() ? null : json.readTree(body).path("code").asText(null);
        return new Outcome(response.getStatus(), code);
    }

    /** role code -> permissions, from the "| Permission | EMPLOYEE | ... | Description |" table. */
    private static Map<String, Set<String>> specRolePermissions() throws IOException {
        List<String> header = header("Permission");
        List<String> roleCodes = header.subList(1, header.indexOf("Description"));
        Map<String, Set<String>> result = new LinkedHashMap<>();
        roleCodes.forEach(code -> result.put(code, new TreeSet<>()));
        for (List<String> row : table("Permission")) {
            for (int i = 0; i < roleCodes.size(); i++) {
                if (!row.get(i + 1).isBlank()) {
                    result.get(roleCodes.get(i)).add(row.get(0));
                }
            }
        }
        return result;
    }

    private static Set<String> permissionsIn(String access) {
        return Arrays.stream(access.split(",")).map(String::trim).collect(Collectors.toSet());
    }

    private static List<String> header(String firstColumn) throws IOException {
        for (String line : Files.readAllLines(MATRIX)) {
            List<String> cells = cells(line);
            if (!cells.isEmpty() && cells.getFirst().equalsIgnoreCase(firstColumn)) {
                return cells;
            }
        }
        throw new IllegalStateException("No table starting with | " + firstColumn + " | in " + MATRIX);
    }

    /** Body rows of the first table whose header starts with {@code firstColumn}. */
    private static List<List<String>> table(String firstColumn) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        boolean inTable = false;
        for (String line : Files.readAllLines(MATRIX)) {
            List<String> cells = cells(line);
            if (!inTable) {
                inTable = !cells.isEmpty() && cells.getFirst().equalsIgnoreCase(firstColumn);
                continue;
            }
            if (!line.trim().startsWith("|")) {
                break;
            }
            if (!cells.getFirst().startsWith("---")) {
                rows.add(cells);
            }
        }
        assertThat(rows).as("rows of the %s table in %s", firstColumn, MATRIX).isNotEmpty();
        return rows;
    }

    private static List<String> cells(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("|")) {
            return List.of();
        }
        String inner = trimmed.substring(1, trimmed.endsWith("|") ? trimmed.length() - 1 : trimmed.length());
        return Arrays.stream(inner.split("\\|", -1)).map(String::trim).toList();
    }
}
