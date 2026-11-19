package com.warden.user.service;

import java.util.Set;

/**
 * The facts an access decision needs about a person: who, how senior, which departments.
 * {@link UserAccessPolicy} depends on this small interface, not on the JPA entity or the security
 * principal, so its rules are unit-tested with plain records (interface segregation).
 */
public interface AccessSubject {

    Long id();

    /** Highest rank among the person's roles. */
    int rank();

    Set<Long> departmentIds();
}
