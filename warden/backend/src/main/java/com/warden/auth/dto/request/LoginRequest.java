package com.warden.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Vui lòng nhập tên đăng nhập")
        @Size(max = 100, message = "Tên đăng nhập quá dài")
        String username,
        @NotBlank(message = "Vui lòng nhập mật khẩu")
        @Size(max = 200, message = "Mật khẩu quá dài")
        String password) {

    /** Never print the password, even in debug logs. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + "]";
    }
}
