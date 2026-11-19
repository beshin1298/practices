-- Users of the system. One role per user (ADMIN | USER), see docs/rbac-matrix.md.
CREATE TABLE users (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username            VARCHAR(50)  NOT NULL,
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(100) NOT NULL,
    full_name           VARCHAR(100) NOT NULL,
    role                VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Embedded in every JWT as claim "ver". Bumping it revokes all tokens issued before.
    token_version       INTEGER      NOT NULL DEFAULT 0,
    last_login_at       TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_users_username       UNIQUE (username),
    CONSTRAINT uk_users_email          UNIQUE (email),
    CONSTRAINT ck_users_role           CHECK (role IN ('ADMIN', 'USER')),
    -- The app lower-cases these before saving; the DB guarantees it so UNIQUE is case-insensitive.
    CONSTRAINT ck_users_username_lower CHECK (username = lower(username)),
    CONSTRAINT ck_users_email_lower    CHECK (email = lower(email)),
    CONSTRAINT ck_users_token_version  CHECK (token_version >= 0)
);

COMMENT ON COLUMN users.token_version IS 'Incremented on password change; JWTs carrying an older value are rejected.';
