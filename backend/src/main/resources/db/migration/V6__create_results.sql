CREATE TABLE results (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL UNIQUE REFERENCES attempts(id) ON DELETE CASCADE,
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    submitted_at TIMESTAMPTZ NOT NULL,
    total_marks NUMERIC(9, 2) NOT NULL,
    maximum_marks NUMERIC(9, 2) NOT NULL,
    percentage NUMERIC(7, 2) NOT NULL,
    correct_count INTEGER NOT NULL,
    incorrect_count INTEGER NOT NULL,
    unattempted_count INTEGER NOT NULL,
    time_taken_minutes INTEGER NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    graded_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT uq_result_exam_candidate UNIQUE (exam_id, candidate_id),
    CONSTRAINT chk_result_maximum_marks CHECK (maximum_marks >= 0),
    CONSTRAINT chk_result_counts CHECK (correct_count >= 0 AND incorrect_count >= 0 AND unattempted_count >= 0),
    CONSTRAINT chk_result_time CHECK (time_taken_minutes >= 0),
    CONSTRAINT chk_result_publication CHECK ((published = FALSE AND published_at IS NULL) OR (published = TRUE AND published_at IS NOT NULL))
);

CREATE INDEX idx_results_exam_publication ON results (exam_id, published, candidate_id);
