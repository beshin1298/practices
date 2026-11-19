package com.warden.user.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.warden.user.entity.User;

/**
 * Lists use the plain paged findAll(spec, pageable): roles and departments are then loaded in
 * batches (hibernate.default_batch_fetch_size), a fixed number of queries per page. They are NOT
 * fetch-joined there, because a collection fetch join with paging makes Hibernate page in memory.
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /** Callers pass an already-normalized (lower-case) username, see {@link com.warden.common.util.Normalizer}. */
    Optional<User> findByUsername(String username);

    /** Everything the JWT filter and session responses need, in one query (single row, no paging). */
    @EntityGraph(attributePaths = {"roles", "roles.permissions", "departments", "directPermissions"})
    Optional<User> findForAuthenticationById(Long id);

    /** One UPDATE in its own short transaction: login runs BCrypt outside any transaction. */
    @Transactional
    @Modifying
    @Query("update User u set u.lastLoginAt = :at where u.id = :id")
    int updateLastLoginAt(@Param("id") Long id, @Param("at") Instant at);

    /** Enabled users holding a role of this rank, except one: "would anyone top-ranked be left?" */
    @Query("select count(distinct u) from User u join u.roles r where r.rank = :rank and u.enabled = true and u.id <> :excludedId")
    long countEnabledWithRankExcept(@Param("rank") int rank, @Param("excludedId") Long excludedId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
