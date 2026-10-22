export type Role = 'ROLE_ADMIN' | 'ROLE_CANDIDATE';
export type QuestionType = 'MCQ_SINGLE' | 'MCQ_MULTI';
export type HeatmapState = 'NOT_VISITED' | 'NOT_ANSWERED' | 'ANSWERED' | 'MARKED_FOR_REVIEW' | 'ANSWERED_AND_MARKED_FOR_REVIEW';
export type ExamStatus = 'DRAFT' | 'UPCOMING' | 'AVAILABLE' | 'IN_PROGRESS' | 'COMPLETED' | 'RESULTS_PENDING' | 'PUBLISHED';

export interface User { id: string; fullName: string; email: string; role: Role; rollNumber?: string; avatarUrl?: string | null; }
export interface Option { id: string; label: string; text: string; }
export interface Question { id: string; type: QuestionType; category: string; difficulty: 'EASY' | 'MEDIUM' | 'HARD'; prompt: string; options: Option[]; correctOptionIds?: string[]; positiveMarks?: number; negativeMarks?: number; }
export interface Exam { id: string; title: string; subject: string; description: string; start: string; end: string; durationMinutes: number; maximumMarks: number; questions: Question[]; status: ExamStatus; attemptStatus?: 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED' | 'EXPIRED'; shuffleQuestions: boolean; shuffleOptions: boolean; resultPublished: boolean; enrolled: number; }
export interface AttemptAnswer { questionId: string; selectedOptionIds: string[]; state: HeatmapState; clientSeq: number; pending?: boolean; error?: string; }
export type AttemptActionName = 'VISIT_QUESTION' | 'SAVE_AND_NEXT' | 'MARK_REVIEW_AND_NEXT' | 'CLEAR_RESPONSE';
export interface AttemptAction { action: AttemptActionName; questionId: string; selectedOptionIds: string[]; clientSeq: number; }
export interface Attempt { id: string; examId: string; effectiveEndEpochMs: number; serverTimeEpochMs: number; status: 'IN_PROGRESS' | 'SUBMITTED' | 'EXPIRED'; questions: Question[]; answers: Record<string, AttemptAnswer>; submitted: boolean; submissionReceipt?: { submissionId: string; acceptedAt: string; status: 'SUBMITTED' | 'EXPIRED' }; }
export interface Result { id: string; examId: string; submittedAt: string; totalMarks: number; maximumMarks: number; percentage: number; correct: number; incorrect: number; unattempted: number; timeTakenMinutes: number; published: boolean; }
export interface ResultPublication { examId: string; publishedCount: number; publishedAt: string; }
export interface Telemetry { status: 'CONNECTED' | 'CONNECTING' | 'RECONNECTING' | 'DISCONNECTED'; lastUpdated?: string; total: number; notStarted: number; inProgress: number; submitted: number; }
