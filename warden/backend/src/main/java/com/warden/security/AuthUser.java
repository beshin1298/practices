package com.warden.security;

import java.security.Principal;
import java.util.Set;

import com.warden.security.filter.JwtAuthenticationFilter;
import com.warden.user.service.Actor;

/**
 * The authenticated caller, put in the SecurityContext by {@link JwtAuthenticationFilter}.
 * Inject it in controllers with {@code @AuthenticationPrincipal AuthUser me}.
 * Roles, permissions and departments are read from the database on every request, not from the
 * token, so a change made by HR or an admin applies to the very next request.
 */
public record AuthUser(Long id, String username, Set<String> roles, Set<String> permissions,
        Set<Long> departmentIds, int rank) implements Principal, Actor {

    @Override
    public String getName() {
        return username;
    }
}
