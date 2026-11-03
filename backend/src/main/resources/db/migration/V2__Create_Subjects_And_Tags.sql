CREATE TABLE subjects (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_subjects_user_name UNIQUE (user_id, name)
);

CREATE TABLE tags (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    CONSTRAINT uq_tags_subject_name UNIQUE (subject_id, name)
);
