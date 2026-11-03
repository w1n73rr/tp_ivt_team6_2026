CREATE TABLE materials (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    subject_id UUID NOT NULL REFERENCES subjects(id),
    title VARCHAR(255) NOT NULL CHECK (length(trim(title)) > 0),
    description VARCHAR(2000),
    file_id UUID NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(10) NOT NULL CHECK (file_type IN ('PDF', 'DOCX', 'TXT')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),
    status VARCHAR(20) NOT NULL DEFAULT 'STORED',
    raw_text TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_materials_user_title UNIQUE (user_id, title)
);
CREATE INDEX ix_materials_subject ON materials(subject_id);

CREATE TABLE material_tags (
    material_id UUID NOT NULL REFERENCES materials(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id),
    PRIMARY KEY (material_id, tag_id)
);

-- No foreign keys: cleanup must survive deletion of the owning entities.
CREATE TABLE material_file_cleanup (
    file_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_material_file_cleanup_due ON material_file_cleanup(available_at);
