package com.warden.role.dto.response;

import java.util.List;

/** A role with its permission codes, so the UI can show the Role x Permission matrix. */
public record RoleResponse(Long id, String code, String name, int rank, List<String> permissions) {
}
