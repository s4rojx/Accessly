CREATE TABLE questions (
    id UUID PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    category VARCHAR(100) NOT NULL,
    difficulty VARCHAR(32) NOT NULL,
    content_markdown TEXT NOT NULL,
    canonical_options JSONB NOT NULL,
    correct_option_indices JSONB NOT NULL,
    positive_marks NUMERIC(7,2) NOT NULL,
    negative_marks NUMERIC(7,2) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_questions_type CHECK (type IN ('MCQ_SINGLE', 'MCQ_MULTI')),
    CONSTRAINT chk_questions_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT chk_questions_content_not_blank CHECK (length(btrim(content_markdown)) > 0),
    CONSTRAINT chk_questions_positive_marks CHECK (positive_marks > 0),
    CONSTRAINT chk_questions_negative_marks CHECK (negative_marks >= 0),
    CONSTRAINT chk_questions_options_array CHECK (jsonb_typeof(canonical_options) = 'array'),
    CONSTRAINT chk_questions_correct_indices_array CHECK (jsonb_typeof(correct_option_indices) = 'array')
);

CREATE INDEX idx_questions_owner_active ON questions (created_by, is_archived);
