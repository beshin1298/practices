package com.warden.auth.controller;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.warden.auth.dto.request.LoginRequest;
import com.warden.auth.dto.response.AuthResponse;
import com.warden.auth.service.AuthService;
import com.warden.auth.service.SessionService;
import com.warden.common.error.AppException;
import com.warden.common.error.ErrorCode;
import com.warden.common.error.Problems;
import com.warden.security.annotation.PublicEndpoint;

/**
 * All three endpoints are PUBLIC: /refresh and /logout must work when the access token has already
 * expired. They authenticate with the HttpOnly refresh cookie instead (see RefreshTokenCookies).
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
@SecurityRequirements
public class AuthController {

    private static final Set<ErrorCode> ENDS_SESSION = Set.of(
            ErrorCode.REFRESH_TOKEN_INVALID, ErrorCode.REFRESH_TOKEN_EXPIRED, ErrorCode.REFRESH_TOKEN_REUSED);

    private final AuthService authService;
    private final SessionService sessionService;
    private final RefreshTokenCookies cookies;

    public AuthController(AuthService authService, SessionService sessionService, RefreshTokenCookies cookies) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.cookies = cookies;
    }

    @PublicEndpoint
    @PostMapping("/login")
    @Operation(summary = "Đăng nhập: trả access token, đặt refresh token vào cookie HttpOnly")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return cookies.respond(authService.login(request, http.getRemoteAddr(), userAgent));
    }

    @PublicEndpoint
    @PostMapping("/refresh")
    @Operation(summary = "Đổi refresh token (cookie) lấy access token mới; refresh token được xoay vòng")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return cookies.respond(sessionService.refresh(refreshToken, userAgent));
    }

    @PublicEndpoint
    @PostMapping("/logout")
    @Operation(summary = "Thu hồi phiên hiện tại và xóa cookie")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        sessionService.end(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.cleared().toString()).build();
    }

    /** Same ProblemDetail as the global handler, plus: a rejected refresh cookie is deleted from the browser. */
    @ExceptionHandler(AppException.class)
    ResponseEntity<ProblemDetail> handle(AppException ex, HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.code().status());
        if (ENDS_SESSION.contains(ex.code())) {
            response.header(HttpHeaders.SET_COOKIE, cookies.cleared().toString());
        }
        return response.body(Problems.of(ex.code(), ex.getMessage(), request.getRequestURI()));
    }
}
