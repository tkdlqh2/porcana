CREATE TABLE admin_batch_job_runs (
    id UUID PRIMARY KEY,
    batch_job_execution_id BIGINT NOT NULL UNIQUE,
    job_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    duration_ms BIGINT,
    summary TEXT,
    error_message TEXT,
    issue_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_batch_job_issues (
    id UUID PRIMARY KEY,
    batch_job_run_id UUID NOT NULL,
    step_name VARCHAR(100),
    asset_id UUID,
    asset_symbol VARCHAR(20),
    asset_name VARCHAR(255),
    issue_code VARCHAR(100) NOT NULL,
    issue_message TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_admin_batch_job_issues_run
        FOREIGN KEY (batch_job_run_id) REFERENCES admin_batch_job_runs(id) ON DELETE CASCADE
);

CREATE INDEX idx_admin_batch_job_runs_created_at ON admin_batch_job_runs (created_at DESC);
CREATE INDEX idx_admin_batch_job_issues_run_id ON admin_batch_job_issues (batch_job_run_id);
CREATE INDEX idx_admin_batch_job_issues_created_at ON admin_batch_job_issues (created_at DESC);
