package com.warden.auth.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.warden.auth.service.RefreshTokenService;

/** Deletes expired refresh tokens off the request path, once a day by default (idempotent). */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenService refreshTokens;

    public RefreshTokenCleanupJob(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Scheduled(cron = "${app.auth.cleanup-cron}")
    public void purgeExpiredTokens() {
        int deleted = refreshTokens.purgeExpired();
        if (deleted > 0) {
            log.info("Purged {} expired refresh token(s)", deleted);
        }
    }
}
