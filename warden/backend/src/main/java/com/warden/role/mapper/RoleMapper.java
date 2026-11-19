package com.warden.role.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.warden.role.dto.response.RoleResponse;
import com.warden.role.dto.response.RoleSummary;
import com.warden.role.entity.Role;

@Mapper
public interface RoleMapper {

    RoleSummary toSummary(Role role);

    @Mapping(target = "permissions", expression = "java(role.permissionCodes())")
    RoleResponse toResponse(Role role);

    List<RoleResponse> toResponses(List<Role> roles);
}
