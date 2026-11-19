package com.warden.auth.service;

import com.warden.auth.dto.response.AuthResponse;

/**
 * What a successful login / refresh / password change produces: the JSON body (access token +
 * profile) and the raw refresh token, which only ever travels in an HttpOnly cookie.
 */
public record IssuedSession(AuthResponse body, String refreshToken) {

    @Override
    public String toString() { // never print the refresh token, even in debug logs
        return "IssuedSession[user=" + body.user().username() + ", refreshToken=***]";
    }
}
