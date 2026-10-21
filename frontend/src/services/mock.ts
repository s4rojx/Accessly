import type { Attempt, AttemptAnswer, Exam, HeatmapState, Result, Role, User } from '../types';

const now = new Date();
const iso = (offsetDays: number, hour: number) => { const d = new Date(now); d.setDate(d.getDate() + offsetDays); d.setHours(hour, 0, 0, 0); return d.toISOString(); };
const questions = [
  { id: 'q1', type: 'MCQ_SINGLE' as const, category: 'Spring Boot', difficulty: 'MEDIUM' as const, prompt: 'Which Spring Boot feature is most appropriate for exposing a health check endpoint?', options: [{ id: 'q1a', label: 'A', text: 'Spring Boot Actuator' }, { id: 'q1b', label: 'B', text: 'Spring Batch' }, { id: 'q1c', label: 'C', text: 'Spring Shell' }, { id: 'q1d', label: 'D', text: 'Spring Session' }], correctOptionIds: ['q1a'], positiveMarks: 2, negativeMarks: 0.5 },
  { id: 'q2', type: 'MCQ_MULTI' as const, category: 'Distributed systems', difficulty: 'HARD' as const, prompt: 'Which properties are important when designing an idempotent Kafka consumer?', options: [{ id: 'q2a', label: 'A', text: 'Stable event identifiers' }, { id: 'q2b', label: 'B', text: 'Monotonic sequence checks' }, { id: 'q2c', label: 'C', text: 'Ignoring all retries' }, { id: 'q2d', label: 'D', text: 'Database uniqueness constraints' }], correctOptionIds: ['q2a', 'q2b', 'q2d'], positiveMarks: 3, negativeMarks: 1 },
  { id: 'q3', type: 'MCQ_SINGLE' as const, category: 'Redis', difficulty: 'EASY' as const, prompt: 'Why is a Redis Lua script useful for saving an exam answer?', options: [{ id: 'q3a', label: 'A', text: 'It makes multiple validations and writes atomic' }, { id: 'q3b', label: 'B', text: 'It permanently replaces PostgreSQL' }, { id: 'q3c', label: 'C', text: 'It runs in the browser' }, { id: 'q3d', label: 'D', text: 'It removes the need for authentication' }], correctOptionIds: ['q3a'], positiveMarks: 2, negativeMarks: 0.5 },
];
export const seedExams: Exam[] = [
  { id: 'exam-1', title: 'Distributed Systems Final', subject: 'Computer Science', description: 'A focused assessment covering Spring Boot, Kafka, Redis, and resilient application design.', start: iso(0, 9), end: iso(0, 12), durationMinutes: 60, maximumMarks: 7, questions, status: 'AVAILABLE', attemptStatus: 'NOT_STARTED', shuffleQuestions: true, shuffleOptions: true, resultPublished: false, enrolled: 42 },
  { id: 'exam-2', title: 'Database Systems Midterm', subject: 'Computer Science', description: 'Relational modeling, indexing, and transaction fundamentals.', start: iso(3, 10), end: iso(3, 12), durationMinutes: 75, maximumMarks: 20, questions: questions.map((q) => ({ ...q, id: `${q.id}-db` })), status: 'UPCOMING', shuffleQuestions: true, shuffleOptions: false, resultPublished: false, enrolled: 38 },
  { id: 'exam-3', title: 'Java Foundations Quiz', subject: 'Software Engineering', description: 'A completed formative quiz with published results.', start: iso(-7, 11), end: iso(-7, 12), durationMinutes: 30, maximumMarks: 20, questions, status: 'PUBLISHED', attemptStatus: 'SUBMITTED', shuffleQuestions: false, shuffleOptions: false, resultPublished: true, enrolled: 56 },
];
let currentUser: User | null = null;
let attempts: Record<string, Attempt> = {};
let results: Record<string, Result> = { 'exam-3': { examId: 'exam-3', totalMarks: 16, maximumMarks: 20, percentage: 80, correct: 8, incorrect: 1, unattempted: 1, timeTakenMinutes: 24, published: true } };

export const mockAuth = {
  async login(email: string, _password: string, role: Role): Promise<User> { currentUser = { id: role === 'ROLE_ADMIN' ? 'admin-1' : 'student-1', fullName: role === 'ROLE_ADMIN' ? 'Dr. Anika Rao' : 'Aarav Mehta', email, role, rollNumber: role === 'ROLE_CANDIDATE' ? 'CS-2026-014' : undefined }; return currentUser; },
  async signup(fullName: string, email: string, rollNumber: string): Promise<User> { currentUser = { id: 'student-1', fullName, email, role: 'ROLE_CANDIDATE', rollNumber }; return currentUser; },
  async logout() { currentUser = null; },
  getUser() { return currentUser; },
};
export const mockApi = {
  async listExams() { return seedExams; },
  async getExam(id: string) { return seedExams.find((e) => e.id === id) ?? seedExams[0]; },
  async createExam(input: Partial<Exam>) { const exam = { ...seedExams[1], ...input, id: `exam-${Date.now()}` } as Exam; seedExams.push(exam); return exam; },
  async publishExam(id: string) { const exam = seedExams.find((e) => e.id === id); if (exam) exam.status = 'AVAILABLE'; return exam; },
  async startAttempt(examId: string): Promise<Attempt> { const id = `${examId}-attempt`; const attempt = attempts[id] ?? { id, examId, userId: 'student-1', effectiveEndEpochMs: Date.now() + 60 * 60 * 1000, answers: {}, submitted: false }; attempts[id] = attempt; return attempt; },
  async action(examId: string, answer: AttemptAnswer): Promise<AttemptAnswer> { const attempt = attempts[`${examId}-attempt`]; if (!attempt || attempt.submitted) throw new Error('ATTEMPT_ALREADY_CLOSED'); await new Promise((r) => setTimeout(r, 120)); attempt.answers[answer.questionId] = { ...answer, pending: false, error: undefined }; return attempt.answers[answer.questionId]; },
  async submit(examId: string) { const attempt = attempts[`${examId}-attempt`]; if (!attempt) throw new Error('ATTEMPT_NOT_FOUND'); if (!attempt.submitted) { attempt.submitted = true; attempt.submissionReceipt = { submissionId: `submission-${Date.now()}`, acceptedAt: new Date().toISOString(), status: 'ACCEPTED' }; } return attempt.submissionReceipt; },
  async getResult(examId: string) { return results[examId] ?? { examId, totalMarks: 0, maximumMarks: 7, percentage: 0, correct: 0, incorrect: 0, unattempted: 3, timeTakenMinutes: 0, published: false }; },
  async publishResults(examId: string) { results[examId] = { examId, totalMarks: 6, maximumMarks: 7, percentage: 86, correct: 3, incorrect: 0, unattempted: 0, timeTakenMinutes: 41, published: true }; return results[examId]; },
};
export const statusLabel: Record<HeatmapState, string> = { NOT_VISITED: 'Not visited', NOT_ANSWERED: 'Not answered', ANSWERED: 'Answered', MARKED_FOR_REVIEW: 'Marked for review', ANSWERED_AND_MARKED_FOR_REVIEW: 'Answered & review' };
