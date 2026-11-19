package com.warden.department.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.warden.department.dto.response.DepartmentResponse;
import com.warden.department.service.DepartmentService;
import com.warden.security.annotation.Authenticated;

/** Departments: reference data for filters and forms. */
@RestController
@RequestMapping("/api/departments")
@Authenticated
@Tag(name = "Departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @Operation(summary = "Danh sách phòng ban")
    public List<DepartmentResponse> list() {
        return departmentService.listDepartments();
    }
}
