package com.warden.user.dto.request;

import jakarta.validation.constraints.NotBlank;

import com.warden.common.validation.ValidPassword;

/** "Confirm password" is checked by the frontend only; the API needs just the two values. */
public record ChangePasswordRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
        String currentPassword,

        @ValidPassword
        String newPassword) {

    /** Never print either password, even in debug logs. */
    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
