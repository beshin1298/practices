package com.warden.user.dto.request;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Replaces the user's departments (many-to-many) with this set. */
public record AssignDepartmentsRequest(
        @NotEmpty(message = "Chọn ít nhất một phòng ban")
        @Size(max = 10, message = "Tối đa 10 phòng ban")
        Set<@NotNull(message = "Phòng ban không hợp lệ") Long> departmentIds) {
}
