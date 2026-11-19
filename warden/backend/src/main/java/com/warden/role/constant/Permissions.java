package com.warden.role.constant;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Permission codes the code checks. Rows live in the permissions table (V4); which role has which
 * permission is data (role_permissions). Each code is also a Spring Security authority, used by
 * {@code @PreAuthorize("hasAuthority('...')")} and the URL rules in SecurityConfig.
 * rbac_audit.py flags any code used in the matrix or the code that is not declared here.
 */
public final class Permissions {

    public static final String USER_READ_OWN = "USER_READ_OWN";
    public static final String USER_UPDATE_OWN = "USER_UPDATE_OWN";
    public static final String USER_READ_DEPARTMENT = "USER_READ_DEPARTMENT";
    public static final String USER_UPDATE_DEPARTMENT = "USER_UPDATE_DEPARTMENT";
    public static final String USER_READ_ALL = "USER_READ_ALL";
    public static final String USER_UPDATE_ALL = "USER_UPDATE_ALL";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_DISABLE = "USER_DISABLE";
    public static final String USER_ASSIGN_ROLE = "USER_ASSIGN_ROLE";
    public static final String RBAC_READ = "RBAC_READ";
    public static final String RBAC_UPDATE = "RBAC_UPDATE";

    public static final Set<String> ALL = Set.of(USER_READ_OWN, USER_UPDATE_OWN, USER_READ_DEPARTMENT,
            USER_UPDATE_DEPARTMENT, USER_READ_ALL, USER_UPDATE_ALL, USER_CREATE, USER_DISABLE, USER_ASSIGN_ROLE,
            RBAC_READ, RBAC_UPDATE);

    /**
     * For handing out permissions only (not for access checks): a wider scope covers a narrower one.
     * Holding USER_READ_ALL lets you give USER_READ_DEPARTMENT and USER_READ_OWN, and so on.
     */
    private static final Map<String, List<String>> IMPLIES = Map.of(
            USER_READ_ALL, List.of(USER_READ_DEPARTMENT, USER_READ_OWN),
            USER_READ_DEPARTMENT, List.of(USER_READ_OWN),
            USER_UPDATE_ALL, List.of(USER_UPDATE_DEPARTMENT, USER_UPDATE_OWN),
            USER_UPDATE_DEPARTMENT, List.of(USER_UPDATE_OWN));

    /** The codes plus every narrower code they cover. */
    public static Set<String> withImplied(Collection<String> codes) {
        Set<String> result = new HashSet<>(codes);
        codes.forEach(code -> result.addAll(IMPLIES.getOrDefault(code, List.of())));
        return result;
    }

    /** Codes added or removed between two sets: the ones a granter must hold. */
    public static Set<String> changed(Collection<String> before, Collection<String> after) {
        Set<String> diff = new HashSet<>(before);
        diff.addAll(after);
        diff.removeIf(code -> before.contains(code) && after.contains(code));
        return diff;
    }

    private Permissions() {
    }
}
