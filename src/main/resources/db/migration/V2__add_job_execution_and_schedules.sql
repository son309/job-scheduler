ALTER TABLE jobs
    ADD COLUMN next_attempt_at DATETIME(6) NULL,
    ADD COLUMN worker_id VARCHAR(100) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_jobs_retry
    ON jobs(status, next_attempt_at);

CREATE INDEX idx_jobs_recovery
    ON jobs(status, updated_at);

CREATE TABLE job_schedules (
    id CHAR(36) NOT NULL,
    job_type VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    max_retries INT NOT NULL DEFAULT 3,
    timeout_seconds INT NOT NULL DEFAULT 60,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_at DATETIME(6) NOT NULL,
    last_run_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_job_schedules_due
    ON job_schedules(enabled, next_run_at);
