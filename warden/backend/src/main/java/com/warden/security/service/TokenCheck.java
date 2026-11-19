package com.warden.security.service;

import com.warden.common.error.ErrorCode;
import com.warden.security.AuthUser;

/** Outcome of checking a bearer token: the caller it belongs to, or why it was refused. */
public sealed interface TokenCheck {

    record Accepted(AuthUser principal) implements TokenCheck {
    }

    /** {@code reason} is TOKEN_EXPIRED, TOKEN_INVALID or TOKEN_REVOKED. */
    record Rejected(ErrorCode reason) implements TokenCheck {
    }
}
