-- Refresh tokens are opaque random values (256 bits). Only their SHA-256 is stored, so a leaked
-- database dump cannot be replayed. Each login starts a "family"; every refresh rotates the token
-- inside the family. Presenting an already-rotated token revokes the whole family (reuse detection).
CREATE TABLE refresh_tokens (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash     VARCHAR(64)  NOT NULL,
    family_id      UUID         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    -- Set when the token was rotated: replaying it is reuse, not a normal logout.
    replaced_by_id BIGINT       REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    user_agent     VARCHAR(255),

    CONSTRAINT uk_refresh_tokens_hash   UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > created_at)
);

-- Lookups: by hash (unique index above), revoke by user / by family, purge by expiry.
CREATE INDEX ix_refresh_tokens_user    ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family  ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);
