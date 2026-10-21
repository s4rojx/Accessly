CREATE TABLE exams (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    subject VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    window_start_time TIMESTAMPTZ NOT NULL,
    window_end_time TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL,
    shuffle_questions BOOLEAN NOT NULL DEFAULT TRUE,
    shuffle_options BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_exams_window CHECK (window_end_time > window_start_time),
    CONSTRAINT chk_exams_duration CHECK (duration_minutes > 0),
    CONSTRAINT chk_exams_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT chk_exams_duration_in_window CHECK (window_start_time + make_interval(mins => duration_minutes) <= window_end_time)
);

CREATE INDEX idx_exams_owner_status ON exams (created_by, status, window_start_time);

CREATE TABLE exam_questions (
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions(id),
    sequence_number INTEGER NOT NULL,
    PRIMARY KEY (exam_id, question_id),
    CONSTRAINT uq_exam_question_sequence UNIQUE (exam_id, sequence_number),
    CONSTRAINT chk_exam_question_sequence CHECK (sequence_number >= 0)
);

CREATE TABLE exam_allowed_candidates (
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (exam_id, candidate_id)
);

CREATE INDEX idx_exam_allowed_candidates_candidate ON exam_allowed_candidates (candidate_id, exam_id);
