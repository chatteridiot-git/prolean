CREATE TABLE task (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    request_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    duration_days INTEGER GENERATED ALWAYS AS (end_date - start_date + 1) STORED,
    pic_user_id UUID NOT NULL,
    pic_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    percent_complete INTEGER NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT task_request_id_unique UNIQUE (request_id),
    CONSTRAINT task_dates_check CHECK (end_date >= start_date),
    CONSTRAINT task_status_check CHECK (status IN ('OPEN', 'IN_PROGRESS', 'CLOSED')),
    CONSTRAINT task_percent_check CHECK (percent_complete BETWEEN 0 AND 100),
    CONSTRAINT task_closed_complete_check CHECK (status <> 'CLOSED' OR percent_complete = 100)
);

CREATE INDEX ix_task_project_status ON task (project_id, status);
CREATE INDEX ix_task_project_pic ON task (project_id, pic_user_id);
CREATE INDEX ix_task_project_start ON task (project_id, start_date);
