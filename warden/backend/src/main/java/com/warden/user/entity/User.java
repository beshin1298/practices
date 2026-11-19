package com.warden.user.entity;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.warden.department.entity.Department;
import com.warden.role.entity.Permission;
import com.warden.role.entity.Role;

/**
 * Mapped to "users" (V1, V4). Roles and departments are many-to-many (user_roles, user_departments)
 * and LAZY: loaded only where needed, in batches for lists (hibernate.default_batch_fetch_size) or
 * with an entity graph for the JWT filter. Password changes go through {@link #changePassword} so
 * the token version is always bumped with it.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String username;

    @Setter
    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Setter
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Setter
    @Column(length = 20)
    private String phone;

    @Setter
    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @ManyToMany
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @OrderBy("rank DESC")
    private Set<Role> roles = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(name = "user_departments",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id"))
    @OrderBy("name ASC")
    private Set<Department> departments = new LinkedHashSet<>();

    /** Extra permissions for this user on top of their roles ("quyền lẻ"). They never change the rank. */
    @ManyToMany
    @JoinTable(name = "user_permissions",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @OrderBy("code ASC")
    private Set<Permission> directPermissions = new LinkedHashSet<>();

    @Setter
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Setter
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User(String username, String email, String fullName, String passwordHash) {
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
    }

    /** New hash + bump token version: every JWT issued before this moment stops working. */
    public void changePassword(String newPasswordHash, Instant now) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = now;
        this.tokenVersion++;
    }

    public void replaceRoles(Collection<Role> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
    }

    public void replaceDepartments(Collection<Department> newDepartments) {
        departments.clear();
        departments.addAll(newDepartments);
    }

    /** Highest rank among the user's roles; -1 without roles. */
    public int rank() {
        return roles.stream().mapToInt(Role::getRank).max().orElse(-1);
    }

    /** Union of the permissions of all roles, sorted. */
    public void replaceDirectPermissions(Collection<Permission> permissions) {
        directPermissions.clear();
        directPermissions.addAll(permissions);
    }

    public List<String> directPermissionCodes() {
        return directPermissions.stream().map(Permission::getCode).sorted().toList();
    }

    /** Effective permissions: the union of every role's permissions and the direct ones. */
    public List<String> permissionCodes() {
        return Stream.concat(
                        roles.stream().flatMap(role -> role.permissionCodes().stream()),
                        directPermissions.stream().map(Permission::getCode))
                .distinct().sorted().toList();
    }

    public Set<Long> departmentIds() {
        return departments.stream().map(Department::getId).collect(Collectors.toUnmodifiableSet());
    }
}
