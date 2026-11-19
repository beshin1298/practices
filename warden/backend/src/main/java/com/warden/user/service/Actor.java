package com.warden.user.service;

import java.util.Collection;
import java.util.Set;

import com.warden.role.constant.Permissions;

/** The person performing an action: an {@link AccessSubject} plus the permissions of all their roles. */
public interface Actor extends AccessSubject {

    Set<String> permissions();

    default boolean has(String permission) {
        return permissions().contains(permission);
    }

    /**
     * True when the actor holds every one of these permissions, a wider scope covering a narrower one
     * ({@link Permissions#withImplied}): nobody hands out what they do not have.
     */
    default boolean holdsAll(Collection<String> codes) {
        return Permissions.withImplied(permissions()).containsAll(codes);
    }
}
