package com.warden.user.dto.request;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Replaces the user's direct permissions ("quyền lẻ") with this set. Empty = only what the roles give. */
public record AssignPermissionsRequest(
        @NotNull(message = "Gửi danh sách quyền (có thể rỗng)")
        @Size(max = 50, message = "Tối đa 50 quyền")
        Set<@NotBlank(message = "Mã quyền không hợp lệ") String> permissions) {
}
