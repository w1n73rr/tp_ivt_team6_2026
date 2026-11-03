CREATE TABLE generation_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    idempotency_key UUID,
    request_json TEXT NOT NULL,
    material_id UUID NOT NULL,
    file_id UUID NOT NULL,
    format VARCHAR(10) NOT NULL,
    material_name VARCHAR(255) NOT NULL,
    subject_id UUID NOT NULL,
    subject_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')),
    error_code VARCHAR(100),
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, idempotency_key)
);
CREATE INDEX ix_generation_jobs_pending ON generation_jobs(created_at) WHERE status = 'QUEUED';
CREATE INDEX ix_generation_jobs_material ON generation_jobs(material_id, status);

-- A material can be deleted later; snapshots survive. The job is the deduplication boundary.
CREATE TABLE quizzes (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE REFERENCES generation_jobs(id),
    user_id UUID NOT NULL REFERENCES users(id),
    summary_json TEXT NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_quizzes_user_created ON quizzes(user_id, created_at DESC);
