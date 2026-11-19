package com.warden.department.service;

import java.util.List;

import com.warden.department.dto.response.DepartmentResponse;

/** Read side of departments: the reference list used by filters and forms. */
public interface DepartmentService {

    /** All departments sorted by name. */
    List<DepartmentResponse> listDepartments();
}
