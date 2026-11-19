package com.warden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.warden.department.service.DepartmentService;
import com.warden.security.AuthUser;
import com.warden.security.service.TokenAuthenticationService;
import com.warden.security.service.TokenCheck;

/**
 * Every failure, wherever it is thrown, answers with a ProblemDetail that has a stable "code" and
 * never echoes internal details (SQL, class names, connection strings).
 * The token check and one service are mocked so that failures can be forced on purpose.
 */
class ErrorHandlingIntegrationTest extends IntegrationTestSupport {

    private static final String GOOD = "good-token";
    private static final String EXPLODING = "exploding-token";
    private static final String SECRET = "jdbc:postgresql://db-internal:5432 password=hunter2";

    @MockitoBean
    private TokenAuthenticationService tokenAuthentication;

    @MockitoBean
    private DepartmentService departmentService;

    @BeforeEach
    void stubTokens() {
        AuthUser hr = new AuthUser(1L, "hr", Set.of("HR"),
                Set.of("USER_READ_OWN", "USER_UPDATE_OWN", "USER_READ_ALL"), Set.of(), 2);
        when(tokenAuthentication.authenticate(GOOD)).thenReturn(new TokenCheck.Accepted(hr));
        when(tokenAuthentication.authenticate(EXPLODING)).thenThrow(new IllegalStateException(SECRET));
    }

    @Test
    @DisplayName("An exception inside the filter chain (DB down during the token check) is a 500 ProblemDetail")
    void filterChainErrorBecomesProblemDetail() throws Exception {
        assertHidesSecret(call(get("/api/users/me"), EXPLODING)
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")));
    }

    @Test
    @DisplayName("An unexpected exception in a service is a 500 without internal details")
    void unexpectedServiceErrorHidesDetails() throws Exception {
        when(departmentService.listDepartments()).thenThrow(new IllegalStateException(SECRET));

        assertHidesSecret(call(get("/api/departments"), GOOD)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Đã có lỗi xảy ra, vui lòng thử lại")));
    }

    @Test
    @DisplayName("A constraint hit by a race is a 409 DATA_CONFLICT, the SQL message stays in the log")
    void dataIntegrityViolationIsConflict() throws Exception {
        when(departmentService.listDepartments()).thenThrow(new DataIntegrityViolationException(SECRET));

        assertHidesSecret(call(get("/api/departments"), GOOD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT")));
    }

    @Test
    @DisplayName("Malformed JSON and a non-numeric id are 400 BAD_REQUEST")
    void malformedRequestsAreBadRequest() throws Exception {
        call(put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"fullName\": "), GOOD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        call(get("/api/users/abc"), GOOD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("Unknown path is 404 NOT_FOUND without echoing the path; wrong method is 405")
    void unknownPathAndWrongMethod() throws Exception {
        call(get("/api/internal-admin-panel"), GOOD)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Không tìm thấy tài nguyên"));
        call(delete("/api/users/me"), GOOD)
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("HTTP_405"));
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String token) throws Exception {
        return mvc.perform(request.header(AUTHORIZATION, bearer(token)));
    }

    private static void assertHidesSecret(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("hunter2", "db-internal", "IllegalStateException");
    }
}
