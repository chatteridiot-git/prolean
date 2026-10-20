CREATE TABLE timeline (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    request_id UUID NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT timeline_project_unique UNIQUE (project_id),
    CONSTRAINT timeline_request_id_unique UNIQUE (request_id)
);

CREATE TABLE timeline_version (
    id UUID PRIMARY KEY,
    timeline_id UUID NOT NULL REFERENCES timeline (id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    lifecycle_status VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    operational_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    percent_actual INTEGER NOT NULL DEFAULT 0,
    revision_notes VARCHAR(1000),
    request_id UUID NOT NULL,
    publish_request_id UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_by UUID,
    published_at TIMESTAMPTZ,
    superseded_by UUID,
    superseded_at TIMESTAMPTZ,
    snapshot_duration_workdays INTEGER,
    snapshot_percent_plan NUMERIC(5,2),
    snapshot_percent_actual INTEGER,
    snapshot_as_of_date DATE,
    cancelled_by UUID,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT timeline_version_number_unique UNIQUE (timeline_id, version_number),
    CONSTRAINT timeline_version_request_id_unique UNIQUE (request_id),
    CONSTRAINT timeline_version_publish_request_id_unique UNIQUE (publish_request_id),
    CONSTRAINT timeline_version_lifecycle_check CHECK (
        lifecycle_status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'CANCELLED')),
    CONSTRAINT timeline_version_operational_check CHECK (
        operational_status IN ('OPEN', 'IN_PROGRESS', 'CLOSED')),
    CONSTRAINT timeline_version_percent_check CHECK (percent_actual BETWEEN 0 AND 100),
    CONSTRAINT timeline_version_dates_check CHECK (end_date >= start_date),
    CONSTRAINT timeline_version_closed_complete_check CHECK (
        operational_status <> 'CLOSED' OR percent_actual = 100),
    CONSTRAINT timeline_version_draft_defaults_check CHECK (
        lifecycle_status NOT IN ('DRAFT', 'CANCELLED')
        OR (operational_status = 'OPEN' AND percent_actual = 0)),
    CONSTRAINT timeline_version_publish_fields_check CHECK (
        lifecycle_status <> 'PUBLISHED' OR (published_by IS NOT NULL AND published_at IS NOT NULL))
);

CREATE UNIQUE INDEX ux_timeline_version_one_draft
    ON timeline_version (timeline_id) WHERE lifecycle_status = 'DRAFT';
CREATE UNIQUE INDEX ux_timeline_version_one_published
    ON timeline_version (timeline_id) WHERE lifecycle_status = 'PUBLISHED';

CREATE TABLE holiday (
    id UUID PRIMARY KEY,
    holiday_date DATE NOT NULL,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(20) NOT NULL,
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT holiday_date_unique UNIQUE (holiday_date),
    CONSTRAINT holiday_type_check CHECK (type IN ('NATIONAL', 'JOINT_LEAVE'))
);

CREATE TABLE timeline_version_event (
    id UUID PRIMARY KEY,
    timeline_version_id UUID NOT NULL REFERENCES timeline_version (id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL,
    actor_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    details JSONB,
    CONSTRAINT timeline_version_event_type_check CHECK (
        event_type IN ('CREATED', 'UPDATED', 'PUBLISHED', 'SUPERSEDED',
                       'CANCELLED', 'STATUS_CHANGED', 'PROGRESS_CHANGED'))
);

CREATE INDEX ix_timeline_version_event_version ON timeline_version_event (timeline_version_id, occurred_at);
