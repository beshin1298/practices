package com.warden.security.service;

/**
 * Turns an access token into the current caller. The signature and expiry come from the token;
 * everything else (enabled, token version, roles, permissions, departments) is read from the
 * database, so a change made by HR or an admin applies to the very next request.
 */
public interface TokenAuthenticationService {

    TokenCheck authenticate(String token);
}
