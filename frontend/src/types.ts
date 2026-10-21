export type Role = 'ROLE_ADMIN' | 'ROLE_CANDIDATE';
export type QuestionType = 'MCQ_SINGLE' | 'MCQ_MULTI';
export type HeatmapState = 'NOT_VISITED' | 'NOT_ANSWERED' | 'ANSWERED' | 'MARKED_FOR_REVIEW' | 'ANSWERED_AND_MARKED_FOR_REVIEW';
export type ExamStatus = 'UPCOMING' | 'AVAILABLE' | 'IN_PROGRESS' | 'COMPLETED' | 'RESULTS_PENDING' | 'PUBLISHED';

export interface User { id: string; fullName: string; email: string; role: Role; rollNumber?: string; }
export interface Option { id: string; label: string; text: string; }
export interface Question { id: string; type: QuestionType; category: string; difficulty: 'EASY' | 'MEDIUM' | 'HARD'; prompt: string; options: Option[]; correctOptionIds: string[]; positiveMarks: number; negativeMarks: number; }
export interface Exam { id: string; title: string; subject: string; description: string; start: string; end: string; durationMinutes: number; maximumMarks: number; questions: Question[]; status: ExamStatus; attemptStatus?: 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED'; shuffleQuestions: boolean; shuffleOptions: boolean; resultPublished: boolean; enrolled: number; }
export interface AttemptAnswer { questionId: string; selectedOptionIds: string[]; state: HeatmapState; clientSeq: number; pending?: boolean; error?: string; }
export interface Attempt { id: string; examId: string; userId: string; effectiveEndEpochMs: number; answers: Record<string, AttemptAnswer>; submitted: boolean; submissionReceipt?: { submissionId: string; acceptedAt: string; status: 'ACCEPTED' | 'PROCESSING' }; }
export interface Result { examId: string; totalMarks: number; maximumMarks: number; percentage: number; correct: number; incorrect: number; unattempted: number; timeTakenMinutes: number; published: boolean; }
export interface Telemetry { status: 'CONNECTED' | 'CONNECTING' | 'RECONNECTING' | 'DISCONNECTED'; lastUpdated?: string; total: number; notStarted: number; inProgress: number; submitted: number; }
