-- Preserve existing snapshots and answers. If legacy duplicate active attempts exist,
-- this index deliberately rejects the migration instead of discarding or closing user data.
CREATE UNIQUE INDEX attempts_one_active_per_quiz
    ON attempts(user_id, quiz_id) WHERE status = 'IN_PROGRESS';

CREATE TABLE attempt_start_requests (
    user_id UUID NOT NULL REFERENCES users(id),
    idempotency_key VARCHAR(128) NOT NULL,
    attempt_id UUID NOT NULL REFERENCES attempts(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, idempotency_key)
);
CREATE INDEX attempt_start_requests_attempt ON attempt_start_requests(attempt_id);

-- Preserve replay of requests accepted before start/resume began sharing an active attempt.
INSERT INTO attempt_start_requests(user_id, idempotency_key, attempt_id)
    SELECT user_id, idempotency_key, id FROM attempts;
