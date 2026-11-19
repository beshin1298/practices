package com.warden.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.warden.common.web.PageResponse;
import com.warden.security.AuthUser;
import com.warden.user.dto.request.AssignDepartmentsRequest;
import com.warden.user.dto.request.AssignPermissionsRequest;
import com.warden.user.dto.request.AssignRolesRequest;
import com.warden.user.dto.request.CreateUserRequest;
import com.warden.user.dto.request.UpdateStatusRequest;
import com.warden.user.dto.request.UpdateUserRequest;
import com.warden.user.dto.response.UserResponse;
import com.warden.user.service.UserManagementService;

/**
 * Each method states the permission(s) it needs (any of them). The same rule exists as a URL rule in
 * SecurityConfig and as a row in docs/rbac-matrix.md; rbac_audit.py checks the three agree.
 * Which rows of data the caller may touch (department, rank) is decided by UserAccessPolicy.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
public class UserController {

    private final UserManagementService users;

    public UserController(UserManagementService users) {
        this.users = users;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_READ_DEPARTMENT', 'USER_READ_ALL')")
    @Operation(summary = "Danh sách nhân sự trong phạm vi được xem: phân trang, tìm kiếm, lọc phòng ban")
    public PageResponse<UserResponse> list(@AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) @Size(max = 100, message = "Từ khóa tối đa 100 ký tự") String q,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return users.list(me, q, departmentId, page, size, sort, direction);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('USER_READ_DEPARTMENT', 'USER_READ_ALL')")
    @Operation(summary = "Chi tiết một nhân sự trong phạm vi được xem")
    public UserResponse get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return users.get(me, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Tạo tài khoản, gán vai trò và phòng ban")
    public UserResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateUserRequest request) {
        return users.create(me, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE_DEPARTMENT', 'USER_UPDATE_ALL')")
    @Operation(summary = "Sửa hồ sơ nhân sự (email chỉ khi có USER_UPDATE_ALL)")
    public UserResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return users.update(me, id, request);
    }

    @PutMapping("/{id}/departments")
    @PreAuthorize("hasAuthority('USER_UPDATE_ALL')")
    @Operation(summary = "Gán phòng ban (thay toàn bộ danh sách)")
    public UserResponse assignDepartments(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
            @Valid @RequestBody AssignDepartmentsRequest request) {
        return users.assignDepartments(me, id, request.departmentIds());
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USER_ASSIGN_ROLE')")
    @Operation(summary = "Gán vai trò (thay toàn bộ danh sách)")
    public UserResponse assignRoles(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
            @Valid @RequestBody AssignRolesRequest request) {
        return users.assignRoles(me, id, request.roleIds());
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('USER_ASSIGN_ROLE')")
    @Operation(summary = "Gán quyền lẻ (thay toàn bộ danh sách, cộng thêm vào quyền từ vai trò)")
    public UserResponse assignPermissions(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
            @Valid @RequestBody AssignPermissionsRequest request) {
        return users.assignPermissions(me, id, request.permissions());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_DISABLE')")
    @Operation(summary = "Khóa / mở khóa tài khoản")
    public UserResponse changeStatus(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return users.changeStatus(me, id, request.enabled());
    }
}
