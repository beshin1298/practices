package com.warden.role.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.warden.common.error.AppException;
import com.warden.common.error.ErrorCode;
import com.warden.role.entity.Permission;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findByCodeIn(Collection<String> codes);

    /** All the given codes, or PERMISSION_NOT_FOUND when one of them does not exist (one query). */
    default List<Permission> findAllByCodeOrThrow(Collection<String> codes) {
        List<Permission> found = codes.isEmpty() ? List.of() : findByCodeIn(codes);
        if (found.size() != codes.size()) {
            throw new AppException(ErrorCode.PERMISSION_NOT_FOUND);
        }
        return found;
    }
}
