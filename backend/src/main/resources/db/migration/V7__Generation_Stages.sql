ALTER TABLE generation_jobs ADD COLUMN stage VARCHAR(30) NOT NULL DEFAULT 'PREPARING';
ALTER TABLE generation_jobs ADD COLUMN detail_stage VARCHAR(40);
ALTER TABLE generation_jobs ADD COLUMN state_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE generation_jobs ADD COLUMN ml_sequence BIGINT NOT NULL DEFAULT 0;
UPDATE generation_jobs SET stage = 'COMPLETED' WHERE status = 'COMPLETED';
UPDATE generation_jobs SET stage = 'FAILED' WHERE status = 'FAILED';
CREATE INDEX ix_generation_jobs_expired ON generation_jobs(lease_until) WHERE status = 'RUNNING';
