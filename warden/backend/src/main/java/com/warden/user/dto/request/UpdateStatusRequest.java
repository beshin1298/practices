package com.warden.user.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull(message = "Thiếu trạng thái enabled") Boolean enabled) {
}
