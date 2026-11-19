package com.warden.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.warden.department.mapper.DepartmentMapper;
import com.warden.role.mapper.RoleMapper;
import com.warden.user.dto.response.CurrentUserResponse;
import com.warden.user.dto.response.UserResponse;
import com.warden.user.entity.User;

/**
 * Entity -> DTO mapping generated at compile time (see target/generated-sources).
 * The build runs with unmappedTargetPolicy=ERROR, so adding a field to a DTO without a matching
 * source property fails compilation instead of returning null at runtime.
 * passwordHash and tokenVersion are never exposed because no DTO has such fields.
 */
@Mapper(uses = {RoleMapper.class, DepartmentMapper.class})
public interface UserMapper {

    @Mapping(target = "directPermissions", expression = "java(user.directPermissionCodes())")
    UserResponse toResponse(User user);

    @Mapping(target = "permissions", expression = "java(user.permissionCodes())")
    CurrentUserResponse toCurrentUser(User user);
}
