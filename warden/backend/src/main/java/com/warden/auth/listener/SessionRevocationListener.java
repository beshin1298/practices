package com.warden.auth.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.warden.auth.service.RefreshTokenService;
import com.warden.user.event.UserSecurityChangedEvent;

/**
 * Revokes every refresh token of a user whose password changed or who was disabled.
 * A plain (synchronous) @EventListener runs inside the publisher's transaction: the revocation
 * commits or rolls back together with the password change. An AFTER_COMMIT listener could leave
 * old sessions alive if it failed after the password was already changed.
 */
@Component
public class SessionRevocationListener {

    private final RefreshTokenService refreshTokens;

    public SessionRevocationListener(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @EventListener
    public void onUserSecurityChanged(UserSecurityChangedEvent event) {
        refreshTokens.revokeAllForUser(event.userId());
    }
}
