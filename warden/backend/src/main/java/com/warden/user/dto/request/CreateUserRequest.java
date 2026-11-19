package com.warden.user.dto.request;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.warden.common.validation.ValidPassword;

/** HR / admin creating an account: this is an internal app, there is no self-registration. */
public record CreateUserRequest(
        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
                message = "Tên đăng nhập 3-50 ký tự, chỉ gồm chữ không dấu, số, dấu . _ -")
        String username,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email,

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @Pattern(regexp = "^$|^\\+?[0-9][0-9 ]{6,17}[0-9]$", message = "Số điện thoại 8-20 ký tự: chữ số và khoảng trắng, có thể bắt đầu bằng +")
        String phone,

        @Size(max = 100, message = "Chức danh tối đa 100 ký tự")
        String jobTitle,

        @ValidPassword
        String password,

        @NotEmpty(message = "Chọn ít nhất một vai trò")
        @Size(max = 10, message = "Tối đa 10 vai trò")
        Set<@NotNull(message = "Vai trò không hợp lệ") Long> roleIds,

        @NotEmpty(message = "Chọn ít nhất một phòng ban")
        @Size(max = 10, message = "Tối đa 10 phòng ban")
        Set<@NotNull(message = "Phòng ban không hợp lệ") Long> departmentIds) {

    /** Never print the password, even in debug logs. */
    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + ", email=" + email + ", roleIds=" + roleIds
                + ", departmentIds=" + departmentIds + "]";
    }
}
