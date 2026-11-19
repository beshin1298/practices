package com.warden.department.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.warden.department.dto.response.DepartmentResponse;
import com.warden.department.entity.Department;

@Mapper
public interface DepartmentMapper {

    DepartmentResponse toResponse(Department department);

    List<DepartmentResponse> toResponses(List<Department> departments);
}
