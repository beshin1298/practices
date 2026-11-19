package com.warden.role.entity;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

/** A named bundle of permissions (roles <-> permissions is many-to-many, table role_permissions). */
@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30, updatable = false)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** Higher rank = more authority. Used for "nobody acts on someone ranked above them". */
    @Column(nullable = false)
    private int rank;

    @ManyToMany
    @JoinTable(name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @OrderBy("code ASC")
    private Set<Permission> permissions = new LinkedHashSet<>();

    public void replacePermissions(Collection<Permission> newPermissions) {
        permissions.clear();
        permissions.addAll(newPermissions);
    }

    public List<String> permissionCodes() {
        return permissions.stream().map(Permission::getCode).sorted().toList();
    }
}
