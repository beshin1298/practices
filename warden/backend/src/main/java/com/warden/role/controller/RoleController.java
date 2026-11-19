package com.warden.role.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.warden.role.dto.request.UpdateRolePermissionsRequest;
import com.warden.role.dto.response.RoleResponse;
import com.warden.role.service.RoleService;
import com.warden.security.AuthUser;

/** Roles and their permissions: the "Roles & permissions" screen, and the role list used by HR forms. */
@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RBAC_READ')")
    @Operation(summary = "Danh sách vai trò kèm quyền")
    public List<RoleResponse> list() {
        return roleService.listRoles();
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('RBAC_UPDATE')")
    @Operation(summary = "Thay toàn bộ quyền của một vai trò có cấp thấp hơn mình")
    public RoleResponse updatePermissions(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return roleService.updatePermissions(me, id, request.permissions());
    }
}
