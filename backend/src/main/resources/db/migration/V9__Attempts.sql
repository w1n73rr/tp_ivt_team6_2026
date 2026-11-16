CREATE TABLE attempts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    quiz_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    quiz_json TEXT NOT NULL,
    questions_json TEXT NOT NULL,
    answers_json TEXT NOT NULL DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS'
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    started_at TIMESTAMPTZ NOT NULL,
    UNIQUE(user_id, idempotency_key)
);
-- Independent snapshots survive removal of the original quiz/material.
CREATE INDEX attempts_owner ON attempts(user_id, started_at DESC, id DESC);
