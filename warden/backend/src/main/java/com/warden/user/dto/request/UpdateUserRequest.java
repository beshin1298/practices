package com.warden.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A manager (department scope) or HR (company scope) editing someone's profile.
 * Changing the email additionally requires USER_UPDATE_ALL (checked in UserAccessPolicy).
 */
public record UpdateUserRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @Pattern(regexp = "^$|^\\+?[0-9][0-9 ]{6,17}[0-9]$", message = "Số điện thoại 8-20 ký tự: chữ số và khoảng trắng, có thể bắt đầu bằng +")
        String phone,

        @Size(max = 100, message = "Chức danh tối đa 100 ký tự")
        String jobTitle,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email) {
}
