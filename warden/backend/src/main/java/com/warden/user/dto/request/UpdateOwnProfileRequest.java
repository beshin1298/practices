package com.warden.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What an employee may change about themselves (USER_UPDATE_OWN). Email, job title, departments
 * and roles are managed by HR / managers, so they are simply not fields here: a client cannot
 * smuggle them in (no mass assignment).
 */
public record UpdateOwnProfileRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @Pattern(regexp = "^$|^\\+?[0-9][0-9 ]{6,17}[0-9]$", message = "Số điện thoại 8-20 ký tự: chữ số và khoảng trắng, có thể bắt đầu bằng +")
        String phone) {
}
