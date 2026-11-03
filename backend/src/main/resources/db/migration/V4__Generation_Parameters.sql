CREATE TABLE generation_options (
    category VARCHAR(20) NOT NULL,
    option_value VARCHAR(30) NOT NULL,
    available BOOLEAN NOT NULL,
    default_slot INTEGER,
    sort_order INTEGER NOT NULL,
    PRIMARY KEY (category, option_value),
    CONSTRAINT uq_generation_default UNIQUE (category, default_slot),
    CONSTRAINT ck_generation_default CHECK (default_slot IS NULL OR (default_slot = 1 AND available)),
    CONSTRAINT ck_generation_option CHECK (
        (category = 'QUESTION_COUNT' AND option_value IN ('5', '10')) OR
        (category = 'DIFFICULTY' AND option_value IN ('EASY', 'MEDIUM', 'HARD')) OR
        (category = 'QUESTION_TYPE' AND option_value IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'))
    )
);

-- Defaults confirmed by the user on 2026-11-03; see docs/generation-parameters.md.
INSERT INTO generation_options(category, option_value, available, default_slot, sort_order) VALUES
    ('QUESTION_COUNT', '5', TRUE, NULL, 1),
    ('QUESTION_COUNT', '10', TRUE, 1, 2),
    ('DIFFICULTY', 'EASY', TRUE, NULL, 1),
    ('DIFFICULTY', 'MEDIUM', TRUE, 1, 2),
    ('DIFFICULTY', 'HARD', TRUE, NULL, 3),
    ('QUESTION_TYPE', 'SINGLE_CHOICE', TRUE, 1, 1),
    ('QUESTION_TYPE', 'MULTIPLE_CHOICE', TRUE, NULL, 2),
    ('QUESTION_TYPE', 'TRUE_FALSE', TRUE, NULL, 3);

-- Values, not FKs to mutable options: a later change must not rewrite an accepted run.
-- job_id is reserved for the future job service; this migration does not create jobs.
CREATE TABLE generation_settings_snapshots (
    job_id UUID PRIMARY KEY,
    question_count INTEGER NOT NULL CHECK (question_count IN (5, 10)),
    difficulty VARCHAR(10) NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    question_type VARCHAR(30) NOT NULL CHECK (question_type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
