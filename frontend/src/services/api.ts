import { mockApi, mockAuth } from './mock';
import type { Attempt, AttemptAnswer, Exam, Result, Role, User } from '../types';

export interface AssesslyApi {
  login(email: string, password: string, role: Role): Promise<User>;
  logout(): Promise<void>;
  listExams(): Promise<Exam[]>;
  getExam(id: string): Promise<Exam>;
  createExam(input: Partial<Exam>): Promise<Exam>;
  startAttempt(examId: string): Promise<Attempt>;
  sendAttemptAction(examId: string, answer: AttemptAnswer): Promise<AttemptAnswer>;
  submitAttempt(examId: string): Promise<Attempt['submissionReceipt']>;
  getResult(examId: string): Promise<Result>;
  publishResults(examId: string): Promise<Result>;
}

export const mockAdapter: AssesslyApi = {
  login: mockAuth.login,
  logout: mockAuth.logout,
  listExams: mockApi.listExams,
  getExam: mockApi.getExam,
  createExam: mockApi.createExam,
  startAttempt: mockApi.startAttempt,
  sendAttemptAction: mockApi.action,
  submitAttempt: mockApi.submit,
  getResult: mockApi.getResult,
  publishResults: mockApi.publishResults,
};

export class HttpApiAdapter implements AssesslyApi {
  constructor(private readonly baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api/v1') {}
  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 10_000);
    let response: Response;
    try {
      response = await fetch(`${this.baseUrl}${path}`, { ...init, signal: controller.signal, credentials: 'include', headers: { Accept: 'application/json', 'Content-Type': 'application/json', ...init?.headers } });
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') throw new Error('API request timed out');
      throw new Error('Unable to reach the API');
    } finally {
      window.clearTimeout(timeout);
    }
    if (!response.ok) throw new Error(`API request failed with status ${response.status}`);
    return response.json() as Promise<T>;
  }
  login(email: string, password: string, role: Role) { return this.request<User>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password, role }) }); }
  logout() { return this.request<void>('/auth/logout', { method: 'POST' }); }
  listExams() { return this.request<Exam[]>('/student/exams'); }
  async getExam(id: string) { const exams = await this.listExams(); const exam = exams.find((candidate) => candidate.id === id); if (!exam) throw new Error('EXAM_NOT_FOUND'); return exam; }
  createExam(input: Partial<Exam>) { return this.request<Exam>('/admin/exams', { method: 'POST', body: JSON.stringify(input) }); }
  startAttempt(examId: string) { return this.request<Attempt>(`/student/exams/${examId}/start`, { method: 'POST' }); }
  sendAttemptAction(examId: string, answer: AttemptAnswer) { return this.request<AttemptAnswer>(`/student/exams/${examId}/action`, { method: 'POST', body: JSON.stringify(answer) }); }
  submitAttempt(examId: string) { return this.request<Attempt['submissionReceipt']>(`/student/exams/${examId}/submit`, { method: 'POST' }); }
  getResult(examId: string) { return this.request<Result>(`/student/exams/${examId}/result`); }
  publishResults(examId: string) { return this.request<Result>(`/admin/exams/${examId}/publish-results`, { method: 'POST' }); }
}

export const api: AssesslyApi = import.meta.env.VITE_USE_MOCKS === 'false' ? new HttpApiAdapter() : mockAdapter;
