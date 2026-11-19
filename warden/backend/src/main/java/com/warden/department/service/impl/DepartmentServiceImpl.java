package com.warden.department.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.warden.department.dto.response.DepartmentResponse;
import com.warden.department.mapper.DepartmentMapper;
import com.warden.department.repository.DepartmentRepository;
import com.warden.department.service.DepartmentService;

/** Default {@link DepartmentService}. */
@Service
@Transactional(readOnly = true)
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departments;
    private final DepartmentMapper departmentMapper;

    public DepartmentServiceImpl(DepartmentRepository departments, DepartmentMapper departmentMapper) {
        this.departments = departments;
        this.departmentMapper = departmentMapper;
    }

    @Override
    public List<DepartmentResponse> listDepartments() {
        return departmentMapper.toResponses(departments.findAllByOrderByNameAsc());
    }
}
