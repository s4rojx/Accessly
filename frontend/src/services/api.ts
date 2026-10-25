import type { Attempt, AttemptAction, AttemptAnswer, Exam, Result, ResultPublication, User, Question } from '../types';
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
  restoreSession(): Promise<User | null>;
  getProfile(): Promise<User>;
  updateProfile(input: { fullName: string; rollNumber: string }): Promise<User>;
  uploadAvatar(file: File): Promise<User>;
  removeAvatar(): Promise<User>;
  login(email: string, password: string, portal?: 'admin'): Promise<User>;
  loginWithGoogle(credential: string): Promise<User>;
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
  sendAttemptAction(examId: string, action: AttemptAction): Promise<AttemptAnswer>;
  submitAttempt(examId: string): Promise<Attempt['submissionReceipt']>;
  getResult(examId: string): Promise<Result>;
  publishResults(examId: string): Promise<ResultPublication>;
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
type BackendAttemptQuestion = {
  id: string; type: Question['type']; category: string; difficulty: Question['difficulty']; prompt: string;
  options: BackendOption[]; state: AttemptAnswer['state']; selectedOptionIndices: number[]; clientSeq: number;
};
type BackendAttempt = {
  id: string; examId: string; serverTime: string; effectiveEndTime: string; status: Attempt['status'];
  questions: BackendAttemptQuestion[];
};
type BackendActionResponse = {
  attemptId: string; questionId: string; serverTime: string; effectiveEndTime: string;
  action: AttemptAction['action']; state: AttemptAnswer['state']; selectedOptionIndices: number[]; clientSeq: number;
};
type BackendSubmissionReceipt = { submissionId: string; acceptedAt: string; status: 'SUBMITTED' | 'EXPIRED' };
type BackendResult = {
  id: string; examId: string; submittedAt: string; totalMarks: number; maximumMarks: number; percentage: number;
  correct: number; incorrect: number; unattempted: number; timeTakenMinutes: number; published: boolean;
};
type BackendResultPublication = { examId: string; publishedCount: number; publishedAt: string };
type BackendAuth = { accessToken: string; tokenType: string; expiresInSeconds: number; user: User };
type BackendError = { code?: string; message?: string };

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly code: string, message: string) { super(message); }
}

export class HttpApiAdapter implements AssesslyApi {
  private accessToken: string | null = null;
  private refreshing: Promise<void> | null = null;
  private csrfToken: string | null = null;
  private csrfLoading: Promise<void> | null = null;

  constructor(private readonly baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api/v1') {}

  private async request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
    const method = (init.method ?? 'GET').toUpperCase();
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) await this.ensureCsrfToken();
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 10_000);
    const headers = new Headers(init.headers);
    headers.set('Accept', 'application/json');
    if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
    if (this.accessToken) headers.set('Authorization', `Bearer ${this.accessToken}`);
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method) && this.csrfToken) headers.set('X-XSRF-TOKEN', this.csrfToken);
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

  private async ensureCsrfToken() {
    const cookieToken = this.readCsrfCookie();
    if (cookieToken) {
      this.csrfToken = cookieToken;
      return;
    }
    if (!this.csrfLoading) {
      this.csrfLoading = (async () => {
        const controller = new AbortController();
        const timeout = window.setTimeout(() => controller.abort(), 10_000);
        try {
          const response = await fetch(`${this.baseUrl}/auth/csrf`, {
            credentials: 'include', headers: { Accept: 'application/json' }, signal: controller.signal,
          });
          if (!response.ok) throw new ApiError(response.status, 'CSRF_INITIALIZATION_FAILED', 'Unable to initialize request protection');
          const body = await response.json() as { token?: string };
          this.csrfToken = this.readCsrfCookie() ?? body.token ?? null;
          if (!this.csrfToken) throw new ApiError(0, 'CSRF_INITIALIZATION_FAILED', 'Unable to initialize request protection');
        } catch (error) {
          if (error instanceof ApiError) throw error;
          if (error instanceof DOMException && error.name === 'AbortError') throw new ApiError(0, 'API_TIMEOUT', 'The API request timed out');
          throw new ApiError(0, 'API_UNAVAILABLE', 'Unable to reach the API');
        } finally {
          window.clearTimeout(timeout);
        }
      })().finally(() => { this.csrfLoading = null; });
    }
    await this.csrfLoading;
  }

  private readCsrfCookie(): string | null {
    if (typeof document === 'undefined') return null;
    const token = document.cookie.split('; ').find((value) => value.startsWith('XSRF-TOKEN='))?.slice('XSRF-TOKEN='.length);
    return token ? decodeURIComponent(token) : null;
  }

  private async refresh() {
    if (!this.refreshing) {
      this.refreshing = this.request<BackendAuth>('/auth/refresh', { method: 'POST' }, false)
        .then((auth) => { this.accessToken = auth.accessToken; setCurrentUser(auth.user); })
        .finally(() => { this.refreshing = null; });
    }
    return this.refreshing;
  }

  async restoreSession() {
    try {
      await this.refresh();
      return getCurrentUser();
    } catch (error) {
      if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        this.accessToken = null;
        setCurrentUser(null);
        return null;
      }
      throw error;
    }
  }

  async login(email: string, password: string, portal?: 'admin') {
    const auth = await this.request<BackendAuth>('/auth/login', {
      method: 'POST', body: JSON.stringify({ email, password }),
      headers: portal ? { 'X-Assessly-Portal': portal } : undefined,
    }, false);
    this.accessToken = auth.accessToken;
    setCurrentUser(auth.user);
    return auth.user;
  }

  async loginWithGoogle(credential: string) {
    const auth = await this.request<BackendAuth>('/auth/oauth2/google', {
      method: 'POST', body: JSON.stringify({ credential }),
    }, false);
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

  async getProfile() {
    const user = await this.request<User>('/profile');
    setCurrentUser(user);
    return user;
  }

  async updateProfile(input: { fullName: string; rollNumber: string }) {
    const user = await this.request<User>('/profile', { method: 'PATCH', body: JSON.stringify(input) });
    setCurrentUser(user);
    return user;
  }

  async uploadAvatar(file: File) {
    const body = new FormData();
    body.append('file', file);
    const user = await this.request<User>('/profile/avatar', { method: 'POST', body });
    setCurrentUser(user);
    return user;
  }

  async removeAvatar() {
    const user = await this.request<User>('/profile/avatar', { method: 'DELETE' });
    setCurrentUser(user);
    return user;
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

  async startAttempt(examId: string) { return mapAttempt(await this.request<BackendAttempt>(`/student/exams/${examId}/start`, { method: 'POST' })); }
  async sendAttemptAction(examId: string, action: AttemptAction) {
    const response = await this.request<BackendActionResponse>(`/student/exams/${examId}/action`, {
      method: 'POST',
      body: JSON.stringify({ action: action.action, questionId: action.questionId, displayedOptionIndices: action.selectedOptionIds.map(optionIndex), clientSeq: action.clientSeq }),
    });
    return mapActionResponse(response);
  }
  async submitAttempt(examId: string) { return this.request<BackendSubmissionReceipt>(`/student/exams/${examId}/submit`, { method: 'POST' }); }
  async getResult(examId: string) { return mapResult(await this.request<BackendResult>(`/student/exams/${examId}/result`)); }
  async publishResults(examId: string) { return this.request<BackendResultPublication>(`/admin/exams/${examId}/publish-results`, { method: 'POST' }); }
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

function mapAttempt(attempt: BackendAttempt): Attempt {
  const questions = attempt.questions.map((question) => mapBackendQuestion(question));
  const answers = Object.fromEntries(attempt.questions.map((question) => [question.id, {
    questionId: question.id,
    selectedOptionIds: question.selectedOptionIndices.map((index) => `${question.id}-option-${index}`),
    state: question.state,
    clientSeq: question.clientSeq,
  } satisfies AttemptAnswer]));
  return {
    id: attempt.id,
    examId: attempt.examId,
    serverTimeEpochMs: Date.parse(attempt.serverTime),
    effectiveEndEpochMs: Date.parse(attempt.effectiveEndTime),
    status: attempt.status,
    questions,
    answers,
    submitted: attempt.status !== 'IN_PROGRESS',
  };
}

function mapActionResponse(response: BackendActionResponse): AttemptAnswer {
  return {
    questionId: response.questionId,
    selectedOptionIds: response.selectedOptionIndices.map((index) => `${response.questionId}-option-${index}`),
    state: response.state,
    clientSeq: response.clientSeq,
  };
}

function mapResult(result: BackendResult): Result {
  return { ...result };
}

function optionIndex(optionId: string): number {
  const marker = '-option-';
  const index = Number(optionId.slice(optionId.lastIndexOf(marker) + marker.length));
  return Number.isInteger(index) && index >= 0 ? index : -1;
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
