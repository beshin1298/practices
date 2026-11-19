package com.warden.role.dto.request;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Replaces the role's permissions with this set (codes such as USER_READ_ALL). Empty = no permission. */
public record UpdateRolePermissionsRequest(
        @NotNull(message = "Gửi danh sách quyền (có thể rỗng)")
        @Size(max = 50, message = "Tối đa 50 quyền")
        Set<@NotBlank(message = "Mã quyền không hợp lệ") String> permissions) {
}
