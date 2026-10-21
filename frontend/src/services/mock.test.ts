import { describe, expect, it } from 'vitest';
import { mockApi, mockAuth, seedExams, statusLabel } from './mock';

describe('Assessly mock contracts', () => {
  it('supports role-aware login and deterministic seeded exams', async () => {
    const user = await mockAuth.login('student@demo.test', 'password', 'ROLE_CANDIDATE');
    expect(user.role).toBe('ROLE_CANDIDATE');
    expect(seedExams.find((exam) => exam.id === 'exam-1')?.questions.length).toBe(3);
  });

  it('preserves the five documented heatmap labels', () => {
    expect(Object.keys(statusLabel)).toEqual([
      'NOT_VISITED',
      'NOT_ANSWERED',
      'ANSWERED',
      'MARKED_FOR_REVIEW',
      'ANSWERED_AND_MARKED_FOR_REVIEW',
    ]);
  });

  it('accepts answer actions and prevents actions after submission', async () => {
    const attempt = await mockApi.startAttempt('exam-1');
    const answer = await mockApi.action('exam-1', { questionId: 'q1', selectedOptionIds: ['q1a'], state: 'ANSWERED', clientSeq: 1 });
    expect(answer.pending).toBe(false);
    expect(attempt.answers.q1).toBeDefined();
    const receipt = await mockApi.submit('exam-1');
    expect(receipt?.status).toBe('ACCEPTED');
    await expect(mockApi.action('exam-1', { questionId: 'q2', selectedOptionIds: [], state: 'NOT_ANSWERED', clientSeq: 1 })).rejects.toThrow('ATTEMPT_ALREADY_CLOSED');
    expect(await mockApi.submit('exam-1')).toEqual(receipt);
  });

  it('keeps unpublished results unavailable until publication', async () => {
    const unpublished = await mockApi.getResult('exam-1');
    expect(unpublished.published).toBe(false);
    const published = await mockApi.publishResults('exam-1');
    expect(published.published).toBe(true);
  });
});
