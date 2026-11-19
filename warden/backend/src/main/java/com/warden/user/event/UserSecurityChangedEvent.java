package com.warden.user.event;

/**
 * Published by the user module when existing sessions of a user must stop working. The auth module
 * listens and revokes refresh tokens, so user services never depend on auth (dependency inversion),
 * and new reactions (audit log, e-mail) can be added as listeners without touching the publishers.
 */
public record UserSecurityChangedEvent(Long userId, Reason reason) {

    public enum Reason {
        PASSWORD_CHANGED,
        DISABLED
    }
}
