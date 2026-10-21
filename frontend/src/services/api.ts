import type { Attempt, AttemptAnswer, Exam, Result, User, Question } from '../types';
import { getCurrentUser, setCurrentUser } from './session';

export interface QuestionInput {
  type: 'MCQ_SINGLE' | 'MCQ_MULTI';
  category: string;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  prompt: string;
  options: Array<{ index: number; text: string }>;
  correctOptionIndices: number[];
  positiveMarks: number;
  negativeMarks: number;
}

export interface ExamInput {
  title: string;
  subject: string;
  description: string;
  windowStartTime: string;
  windowEndTime: string;
  durationMinutes: number;
  questionIds: string[];
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
}

export interface AssesslyApi {
  login(email: string, password: string): Promise<User>;
  signup(input: { fullName: string; email: string; rollNumber: string; password: string }): Promise<User>;
  logout(): Promise<void>;
  listExams(): Promise<Exam[]>;
  getExam(id: string): Promise<Exam>;
  listAdminQuestions(): Promise<Question[]>;
  createQuestion(input: QuestionInput): Promise<Question>;
  updateQuestion(id: string, input: QuestionInput): Promise<Question>;
  listAdminExams(): Promise<Exam[]>;
  getAdminExam(id: string): Promise<Exam>;
  createExam(input: ExamInput): Promise<Exam>;
  updateExam(id: string, input: ExamInput): Promise<Exam>;
  publishExam(id: string): Promise<Exam>;
  replaceRoster(id: string, candidateIds: string[]): Promise<void>;
  startAttempt(examId: string): Promise<Attempt>;
  sendAttemptAction(examId: string, answer: AttemptAnswer): Promise<AttemptAnswer>;
  submitAttempt(examId: string): Promise<Attempt['submissionReceipt']>;
  getResult(examId: string): Promise<Result>;
  publishResults(examId: string): Promise<Result>;
}

type BackendOption = { index: number; text: string };
type BackendQuestion = {
  id: string; type: Question['type']; category: string; difficulty: Question['difficulty']; prompt: string;
  options: BackendOption[]; correctOptionIndices?: number[]; positiveMarks?: number; negativeMarks?: number;
};
type BackendExam = {
  id: string; title: string; subject: string; description: string; windowStartTime: string; windowEndTime: string;
  durationMinutes: number; maximumMarks: number; questions: BackendQuestion[]; status: string;
  shuffleQuestions: boolean; shuffleOptions: boolean; enrolled: number; resultPublished?: boolean; attemptStatus?: Exam['attemptStatus'];
};
type BackendAuth = { accessToken: string; tokenType: string; expiresInSeconds: number; user: User };
type BackendError = { code?: string; message?: string };

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly code: string, message: string) { super(message); }
}

export class HttpApiAdapter implements AssesslyApi {
  private accessToken: string | null = null;
  private refreshing: Promise<void> | null = null;

  constructor(private readonly baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api/v1') {}

  private async request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 10_000);
    const headers = new Headers(init.headers);
    headers.set('Accept', 'application/json');
    if (init.body) headers.set('Content-Type', 'application/json');
    if (this.accessToken) headers.set('Authorization', `Bearer ${this.accessToken}`);
    let response: Response;
    try {
      response = await fetch(`${this.baseUrl}${path}`, { ...init, signal: controller.signal, credentials: 'include', headers });
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') throw new ApiError(0, 'API_TIMEOUT', 'The API request timed out');
      throw new ApiError(0, 'API_UNAVAILABLE', 'Unable to reach the API');
    } finally { window.clearTimeout(timeout); }
    if (response.status === 401 && retry && !path.startsWith('/auth/')) {
      await this.refresh();
      return this.request<T>(path, init, false);
    }
    if (!response.ok) {
      let error: BackendError = {};
      try { error = await response.json() as BackendError; } catch { }
      throw new ApiError(response.status, error.code ?? 'API_REQUEST_FAILED', error.message ?? `API request failed with status ${response.status}`);
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }

  private async refresh() {
    if (!this.refreshing) {
      this.refreshing = this.request<BackendAuth>('/auth/refresh', { method: 'POST' }, false)
        .then((auth) => { this.accessToken = auth.accessToken; setCurrentUser(auth.user); })
        .finally(() => { this.refreshing = null; });
    }
    return this.refreshing;
  }

  async login(email: string, password: string) {
    const auth = await this.request<BackendAuth>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }, false);
    this.accessToken = auth.accessToken;
    setCurrentUser(auth.user);
    return auth.user;
  }

  async signup(input: { fullName: string; email: string; rollNumber: string; password: string }) {
    return this.request<User>('/auth/signup', { method: 'POST', body: JSON.stringify(input) }, false);
  }

  async logout() {
    try { await this.request<void>('/auth/logout', { method: 'POST' }, false); }
    finally { this.accessToken = null; setCurrentUser(null); }
  }

  async listExams() { return (await this.request<BackendExam[]>('/student/exams')).map(mapExam); }
  async getExam(id: string) { return mapExam(await this.request<BackendExam>(`/student/exams/${id}`)); }
  async listAdminQuestions() { return (await this.request<BackendQuestion[]>('/admin/questions')).map(mapBackendQuestion); }
  async createQuestion(input: QuestionInput) { return mapBackendQuestion(await this.request<BackendQuestion>('/admin/questions', { method: 'POST', body: JSON.stringify(input) })); }
  async updateQuestion(id: string, input: QuestionInput) { return mapBackendQuestion(await this.request<BackendQuestion>(`/admin/questions/${id}`, { method: 'PUT', body: JSON.stringify(input) })); }
  async listAdminExams() { return (await this.request<BackendExam[]>('/admin/exams')).map(mapExam); }
  async getAdminExam(id: string) { return mapExam(await this.request<BackendExam>(`/admin/exams/${id}`)); }
  async createExam(input: ExamInput) { return mapExam(await this.request<BackendExam>('/admin/exams', { method: 'POST', body: JSON.stringify(input) })); }
  async updateExam(id: string, input: ExamInput) { return mapExam(await this.request<BackendExam>(`/admin/exams/${id}`, { method: 'PUT', body: JSON.stringify(input) })); }
  async publishExam(id: string) { return mapExam(await this.request<BackendExam>(`/admin/exams/${id}/publish`, { method: 'POST' })); }
  async replaceRoster(id: string, candidateIds: string[]) { await this.request(`/admin/exams/${id}/roster`, { method: 'PUT', body: JSON.stringify({ candidateIds }) }); }

  startAttempt(examId: string) { return this.request<Attempt>(`/student/exams/${examId}/start`, { method: 'POST' }); }
  sendAttemptAction(examId: string, answer: AttemptAnswer) { return this.request<AttemptAnswer>(`/student/exams/${examId}/action`, { method: 'POST', body: JSON.stringify(answer) }); }
  submitAttempt(examId: string) { return this.request<Attempt['submissionReceipt']>(`/student/exams/${examId}/submit`, { method: 'POST' }); }
  getResult(examId: string) { return this.request<Result>(`/student/exams/${examId}/result`); }
  publishResults(examId: string) { return this.request<Result>(`/admin/exams/${examId}/publish-results`, { method: 'POST' }); }
}

export const api: AssesslyApi = new HttpApiAdapter();
export { getCurrentUser };

export function mapBackendQuestion(question: BackendQuestion): Question {
  return {
    id: question.id,
    type: question.type,
    category: question.category,
    difficulty: question.difficulty,
    prompt: question.prompt,
    options: question.options.map((option) => ({ id: `${question.id}-option-${option.index}`, label: String.fromCharCode(65 + option.index), text: option.text })),
    correctOptionIds: question.correctOptionIndices?.map((index) => `${question.id}-option-${index}`),
    positiveMarks: question.positiveMarks,
    negativeMarks: question.negativeMarks,
  };
}

function mapExam(exam: BackendExam): Exam {
  return {
    id: exam.id,
    title: exam.title,
    subject: exam.subject,
    description: exam.description,
    start: exam.windowStartTime,
    end: exam.windowEndTime,
    durationMinutes: exam.durationMinutes,
    maximumMarks: exam.maximumMarks,
    questions: exam.questions.map(mapBackendQuestion),
    status: exam.status as Exam['status'],
    attemptStatus: exam.attemptStatus,
    shuffleQuestions: exam.shuffleQuestions,
    shuffleOptions: exam.shuffleOptions,
    resultPublished: exam.resultPublished ?? false,
    enrolled: exam.enrolled,
  };
}
