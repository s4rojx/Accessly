CREATE TABLE attempts (
    id UUID PRIMARY KEY,
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL,
    effective_end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    submitted_at TIMESTAMPTZ,
    submission_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_attempt_exam_candidate UNIQUE (exam_id, candidate_id),
    CONSTRAINT uq_attempt_submission UNIQUE (submission_id),
    CONSTRAINT chk_attempt_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED')),
    CONSTRAINT chk_attempt_end_after_start CHECK (effective_end_time > started_at),
    CONSTRAINT chk_attempt_submitted_state CHECK ((status = 'IN_PROGRESS' AND submitted_at IS NULL AND submission_id IS NULL) OR (status IN ('SUBMITTED', 'EXPIRED') AND submitted_at IS NOT NULL AND submission_id IS NOT NULL))
);

CREATE INDEX idx_attempts_candidate_status ON attempts (candidate_id, status, updated_at);

CREATE TABLE attempt_questions (
    attempt_id UUID NOT NULL REFERENCES attempts(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions(id),
    sequence_number INTEGER NOT NULL,
    displayed_option_indices JSONB NOT NULL,
    PRIMARY KEY (attempt_id, question_id),
    CONSTRAINT uq_attempt_question_sequence UNIQUE (attempt_id, sequence_number),
    CONSTRAINT chk_attempt_question_sequence CHECK (sequence_number >= 0),
    CONSTRAINT chk_attempt_question_options_array CHECK (jsonb_typeof(displayed_option_indices) = 'array')
);

CREATE TABLE attempt_answers (
    attempt_id UUID NOT NULL REFERENCES attempts(id) ON DELETE CASCADE,
    question_id UUID NOT NULL,
    selected_option_indices JSONB NOT NULL DEFAULT '[]'::jsonb,
    state VARCHAR(32) NOT NULL,
    client_seq BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (attempt_id, question_id),
    FOREIGN KEY (attempt_id, question_id) REFERENCES attempt_questions(attempt_id, question_id) ON DELETE CASCADE,
    CONSTRAINT chk_attempt_answer_options_array CHECK (jsonb_typeof(selected_option_indices) = 'array'),
    CONSTRAINT chk_attempt_answer_state CHECK (state IN ('NOT_VISITED', 'NOT_ANSWERED', 'ANSWERED', 'MARKED_FOR_REVIEW', 'ANSWERED_AND_MARKED_FOR_REVIEW')),
    CONSTRAINT chk_attempt_answer_sequence CHECK (client_seq >= 0)
);

CREATE INDEX idx_attempt_answers_attempt ON attempt_answers (attempt_id, updated_at);
