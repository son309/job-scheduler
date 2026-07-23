CREATE TABLE jobs (
                      id CHAR(36) NOT NULL,

                      job_type VARCHAR(100) NOT NULL,
                      payload JSON NOT NULL,
                      status VARCHAR(30) NOT NULL,

                      attempt_count INT NOT NULL DEFAULT 0,
                      max_retries INT NOT NULL DEFAULT 3,
                      timeout_seconds INT NOT NULL DEFAULT 60,

                      idempotency_key VARCHAR(255) NULL,

                      result JSON NULL,
                      error_message TEXT NULL,

                      started_at DATETIME(6) NULL,
                      finished_at DATETIME(6) NULL,
                      created_at DATETIME(6) NOT NULL,
                      updated_at DATETIME(6) NOT NULL,

                      PRIMARY KEY (id),

                      CONSTRAINT uq_jobs_idempotency_key
                          UNIQUE (idempotency_key)
);

CREATE INDEX idx_jobs_status
    ON jobs(status);

CREATE INDEX idx_jobs_job_type
    ON jobs(job_type);

CREATE INDEX idx_jobs_created_at
    ON jobs(created_at);