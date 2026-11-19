package com.warden.role.dto.response;

/** A role as shown on a user: enough for labels and for the client-side rank comparison. */
public record RoleSummary(Long id, String code, String name, int rank) {
}
