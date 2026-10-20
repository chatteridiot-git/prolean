CREATE TABLE auth_credential (
    user_id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT auth_credential_username_unique UNIQUE (username),
    CONSTRAINT auth_credential_username_lowercase CHECK (username = lower(username)),
    CONSTRAINT auth_credential_failed_count_nonnegative CHECK (failed_login_count >= 0)
);

CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    family_id UUID NOT NULL,
    parent_token_id UUID,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(30),
    replaced_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT refresh_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT refresh_token_revoked_reason_check CHECK (
        revoked_reason IS NULL OR revoked_reason IN
        ('ROTATED', 'LOGOUT', 'REUSE', 'PASSWORD_CHANGE', 'ROLE_CHANGE', 'DEACTIVATED')
    )
);

CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
