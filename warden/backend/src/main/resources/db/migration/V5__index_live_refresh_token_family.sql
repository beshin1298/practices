-- The JWT filter asks "does this session (family) still have a live refresh token?" on every request.
-- A family keeps every rotated token until purge (one per refresh, for up to 7 days), so the plain
-- family_id index would scan them all; this partial index holds only live rows, about one per session.
CREATE INDEX ix_refresh_tokens_live_family ON refresh_tokens (family_id) WHERE revoked_at IS NULL;
