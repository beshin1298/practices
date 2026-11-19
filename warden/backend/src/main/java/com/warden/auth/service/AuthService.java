package com.warden.auth.service;

import com.warden.auth.dto.request.LoginRequest;

/** Verifies credentials and starts a session. */
public interface AuthService {

    IssuedSession login(LoginRequest request, String clientIp, String userAgent);
}
