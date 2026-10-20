CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT app_user_username_unique UNIQUE (username),
    CONSTRAINT app_user_email_unique UNIQUE (email),
    CONSTRAINT app_user_username_lowercase CHECK (username = lower(username)),
    CONSTRAINT app_user_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT app_user_username_format CHECK (username ~ '^[a-z0-9._-]{3,50}$')
);

CREATE TABLE role (
    id UUID PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    description VARCHAR(255),
    CONSTRAINT role_name_unique UNIQUE (name),
    CONSTRAINT role_name_check CHECK (name IN ('ADMIN', 'USER'))
);

CREATE TABLE user_role (
    user_id UUID NOT NULL REFERENCES app_user (id),
    role_id UUID NOT NULL REFERENCES role (id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE project (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(2000),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    request_id UUID NOT NULL,
    created_by UUID NOT NULL,
    updated_by UUID,
    archived_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    archived_at TIMESTAMPTZ,
    CONSTRAINT project_code_unique UNIQUE (code),
    CONSTRAINT project_request_id_unique UNIQUE (request_id),
    CONSTRAINT project_code_format CHECK (code ~ '^[A-Z0-9-]{2,30}$'),
    CONSTRAINT project_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT project_date_check CHECK (end_date >= start_date)
);

CREATE TABLE project_member (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user (id),
    added_by UUID NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT project_member_unique UNIQUE (project_id, user_id)
);

CREATE INDEX ix_project_member_user ON project_member (user_id);
CREATE INDEX ix_project_status ON project (status);

INSERT INTO role (id, name, description) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN', 'System administrator'),
    ('00000000-0000-0000-0000-000000000002', 'USER', 'Project member')
ON CONFLICT (name) DO NOTHING;
