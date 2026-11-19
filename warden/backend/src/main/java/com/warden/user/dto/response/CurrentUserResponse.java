package com.warden.user.dto.response;

import java.time.Instant;
import java.util.List;

import com.warden.department.dto.response.DepartmentResponse;
import com.warden.role.dto.response.RoleSummary;

/**
 * The logged-in user, plus the effective permissions (union over roles) so the UI can show only
 * the actions the API will accept. The API still enforces every rule itself.
 */
public record CurrentUserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phone,
        String jobTitle,
        List<RoleSummary> roles,
        List<DepartmentResponse> departments,
        List<String> permissions,
        boolean enabled,
        Instant lastLoginAt,
        Instant passwordChangedAt,
        Instant createdAt) {
}
