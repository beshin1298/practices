package com.warden.user.dto.response;

import java.time.Instant;
import java.util.List;

import com.warden.department.dto.response.DepartmentResponse;
import com.warden.role.dto.response.RoleSummary;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phone,
        String jobTitle,
        List<RoleSummary> roles,
        List<String> directPermissions,
        List<DepartmentResponse> departments,
        boolean enabled,
        Instant lastLoginAt,
        Instant passwordChangedAt,
        Instant createdAt) {
}
