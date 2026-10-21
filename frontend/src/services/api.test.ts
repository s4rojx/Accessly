import { describe, expect, it, vi } from 'vitest';
import { HttpApiAdapter, mapBackendQuestion } from './api';

describe('HTTP API boundary', () => {
  it('maps indexed backend options to the existing option view model', () => {
    const question = mapBackendQuestion({
      id: 'question-1',
      type: 'MCQ_SINGLE',
      category: 'Spring Boot',
      difficulty: 'MEDIUM',
      prompt: 'Which feature exposes health information?',
      options: [{ index: 0, text: 'Actuator' }, { index: 1, text: 'Batch' }],
      correctOptionIndices: [0],
      positiveMarks: 2,
      negativeMarks: 0.5,
    });

    expect(question.options).toEqual([
      { id: 'question-1-option-0', label: 'A', text: 'Actuator' },
      { id: 'question-1-option-1', label: 'B', text: 'Batch' },
    ]);
    expect(question.correctOptionIds).toEqual(['question-1-option-0']);
  });

  it('sends credentials and does not send a client role during login', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      accessToken: 'access-token', tokenType: 'Bearer', expiresInSeconds: 900,
      user: { id: 'user-1', fullName: 'Candidate', email: 'candidate@example.com', role: 'ROLE_CANDIDATE' },
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetch);
    const adapter = new HttpApiAdapter('http://localhost:8080/api/v1');

    await adapter.login('candidate@example.com', 'password');

    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/v1/auth/login', expect.objectContaining({
      credentials: 'include',
      body: JSON.stringify({ email: 'candidate@example.com', password: 'password' }),
    }));
  });
});
