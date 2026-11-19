package com.warden.role.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.warden.role.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    @EntityGraph(attributePaths = "permissions")
    List<Role> findAllByOrderByRankAsc();

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findWithPermissionsById(Long id);

    /**
     * SELECT ... FOR UPDATE on the highest-ranked role. Disabling or demoting a top-ranked user takes
     * this lock first, so two admins acting on each other at the same moment run one after the other
     * and the second sees that nobody would be left.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.rank = (select max(r2.rank) from Role r2)")
    List<Role> lockTopRoles();
}
