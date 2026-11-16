ALTER TABLE attempts ADD COLUMN result_json TEXT;
ALTER TABLE attempts ADD COLUMN completed_at TIMESTAMPTZ;
ALTER TABLE attempts ADD CONSTRAINT attempt_result_status CHECK (
    (status = 'COMPLETED' AND result_json IS NOT NULL AND completed_at IS NOT NULL)
    OR (status <> 'COMPLETED' AND result_json IS NULL AND completed_at IS NULL)
);
