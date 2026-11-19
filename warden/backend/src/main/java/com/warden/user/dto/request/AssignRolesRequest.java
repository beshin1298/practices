package com.warden.user.dto.request;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Replaces the user's roles (many-to-many) with this set. */
public record AssignRolesRequest(
        @NotEmpty(message = "Chọn ít nhất một vai trò")
        @Size(max = 10, message = "Tối đa 10 vai trò")
        Set<@NotNull(message = "Vai trò không hợp lệ") Long> roleIds) {
}
