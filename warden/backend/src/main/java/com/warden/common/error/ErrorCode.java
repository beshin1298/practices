package com.warden.common.error;

import org.springframework.http.HttpStatus;

/**
 * Every error the API returns carries one of these codes in the "code" field of the
 * ProblemDetail body. The frontend switches on the code, never on the message text.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Sai tên đăng nhập hoặc mật khẩu"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token không hợp lệ"),
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã bị thu hồi"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại"),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Phát hiện phiên đăng nhập bị dùng lại; mọi phiên liên quan đã bị thu hồi"),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Đăng nhập sai quá nhiều lần, vui lòng thử lại sau 15 phút"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),
    WRONG_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng"),
    PASSWORD_REUSED(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    DEPARTMENT_NOT_FOUND(HttpStatus.BAD_REQUEST, "Phòng ban không tồn tại"),
    ROLE_NOT_FOUND(HttpStatus.BAD_REQUEST, "Vai trò không tồn tại"),
    PERMISSION_NOT_FOUND(HttpStatus.BAD_REQUEST, "Quyền không tồn tại"),
    LAST_TOP_USER(HttpStatus.CONFLICT, "Phải còn ít nhất một tài khoản cấp cao nhất đang hoạt động"),
    ROLE_LOCKED(HttpStatus.CONFLICT, "Không thể sửa quyền của vai trò ngang hoặc cao hơn cấp của bạn"),
    USERNAME_TAKEN(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại"),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "Email đã được sử dụng"),
    SELF_MODIFICATION(HttpStatus.CONFLICT, "Không thể tự đổi vai trò hoặc tự khóa tài khoản của chính mình"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị trùng hoặc xung đột"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Đã có lỗi xảy ra, vui lòng thử lại");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
