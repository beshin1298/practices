package com.warden.user.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.warden.auth.controller.RefreshTokenCookies;
import com.warden.auth.dto.response.AuthResponse;
import com.warden.auth.service.SessionService;
import com.warden.security.AuthUser;
import com.warden.user.dto.request.ChangePasswordRequest;
import com.warden.user.dto.request.UpdateOwnProfileRequest;
import com.warden.user.dto.response.CurrentUserResponse;
import com.warden.user.service.AccountService;

/**
 * "/me" endpoints: there is no user id in the URL, so a user cannot address someone else's account.
 * The controller is the composition point: AccountService changes the password, SessionService
 * issues the new session; neither service knows about the other.
 */
@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Account")
public class AccountController {

    private final AccountService accountService;
    private final SessionService sessionService;
    private final RefreshTokenCookies cookies;

    public AccountController(AccountService accountService, SessionService sessionService, RefreshTokenCookies cookies) {
        this.accountService = accountService;
        this.sessionService = sessionService;
        this.cookies = cookies;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ_OWN')")
    @Operation(summary = "Hồ sơ của tôi, kèm vai trò, phòng ban và quyền hiệu lực")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthUser me) {
        return accountService.getProfile(me.id());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('USER_UPDATE_OWN')")
    @Operation(summary = "Sửa họ tên, số điện thoại của tôi")
    public CurrentUserResponse updateProfile(@AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody UpdateOwnProfileRequest request) {
        return accountService.updateProfile(me.id(), request);
    }

    @PutMapping("/password")
    @PreAuthorize("hasAuthority('USER_UPDATE_OWN')")
    @Operation(summary = "Đổi mật khẩu; thu hồi mọi phiên cũ, trả phiên mới cho thiết bị này")
    public ResponseEntity<AuthResponse> changePassword(@AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        accountService.changePassword(me.id(), request);
        return cookies.respond(sessionService.start(me.id(), userAgent));
    }
}
