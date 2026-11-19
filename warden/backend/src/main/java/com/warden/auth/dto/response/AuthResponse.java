package com.warden.auth.dto.response;

import com.warden.user.dto.response.CurrentUserResponse;

/** @param expiresIn seconds until accessToken expires. The refresh token travels only in a cookie. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, CurrentUserResponse user) {
}
