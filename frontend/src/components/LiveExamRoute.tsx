import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { CheckCircle2, Clock3, Link2, Send, ShieldCheck, X } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { ExamQuestionPanel } from './ExamQuestionPanel';
import { ApiError, api } from '../services/api';
import type { Attempt, AttemptActionName } from '../types';

function formatRemaining(milliseconds: number) {
  const seconds = Math.floor(milliseconds / 1000);
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
}

export function LiveExamRoute() {
  const { examId } = useParams();
  const [attempt, setAttempt] = useState<Attempt | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [draftSelections, setDraftSelections] = useState<Record<string, string[]>>({});
  const [remaining, setRemaining] = useState(0);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [receipt, setReceipt] = useState<Attempt['submissionReceipt']>();
  const clockOffset = useRef(0);
  const visited = useRef(new Set<string>());
  const submitting = useRef(false);

  useEffect(() => {
    if (!examId) return;
    api.startAttempt(examId).then((loaded) => {
      setAttempt(loaded);
      clockOffset.current = loaded.serverTimeEpochMs - Date.now();
      setDraftSelections(Object.fromEntries(Object.entries(loaded.answers).map(([id, answer]) => [id, answer.selectedOptionIds])));
    }).catch((reason) => setError(reason instanceof ApiError ? reason.message : 'Unable to start the examination.'));
  }, [examId]);

  useEffect(() => {
    if (!attempt) return;
    const tick = () => setRemaining(Math.max(0, attempt.effectiveEndEpochMs - (Date.now() + clockOffset.current)));
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [attempt]);

  const submit = useCallback(async () => {
    if (!examId || !attempt || submitting.current || attempt.status !== 'IN_PROGRESS') return;
    submitting.current = true;
    setPending(true);
    setError('');
    try {
      const submitted = await api.submitAttempt(examId);
      setReceipt(submitted);
      setAttempt((current) => current ? { ...current, status: submitted?.status ?? 'SUBMITTED', submitted: true, submissionReceipt: submitted } : current);
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : 'Unable to submit the examination.');
      submitting.current = false;
    } finally {
      setPending(false);
    }
  }, [attempt, examId]);

  useEffect(() => {
    if (remaining === 0 && attempt?.status === 'IN_PROGRESS' && !receipt) void submit();
  }, [attempt?.status, receipt, remaining, submit]);

  const currentQuestion = attempt?.questions[currentIndex];
  const currentAnswer = currentQuestion && attempt?.answers[currentQuestion.id];
  const selected = useMemo(() => currentQuestion ? (draftSelections[currentQuestion.id] ?? currentAnswer?.selectedOptionIds ?? []) : [], [currentAnswer?.selectedOptionIds, currentQuestion, draftSelections]);

  const sendAction = useCallback(async (action: AttemptActionName, questionId: string, selectedOptionIds: string[], advance = false) => {
    if (!examId || !attempt || pending) return false;
    const previous = attempt.answers[questionId];
    const optimistic = { questionId, selectedOptionIds, state: action === 'MARK_REVIEW_AND_NEXT'
      ? selectedOptionIds.length ? 'ANSWERED_AND_MARKED_FOR_REVIEW' as const : 'MARKED_FOR_REVIEW' as const
      : selectedOptionIds.length ? 'ANSWERED' as const : 'NOT_ANSWERED' as const, clientSeq: (previous?.clientSeq ?? 0) + 1, pending: true };
    setAttempt((current) => current ? { ...current, answers: { ...current.answers, [questionId]: optimistic } } : current);
    setPending(true);
    setError('');
    try {
      const saved = await api.sendAttemptAction(examId, { action, questionId, selectedOptionIds, clientSeq: optimistic.clientSeq });
      setAttempt((current) => current ? { ...current, answers: { ...current.answers, [questionId]: saved } } : current);
      setDraftSelections((current) => ({ ...current, [questionId]: saved.selectedOptionIds }));
      if (advance) setCurrentIndex((current) => Math.min(current + 1, attempt.questions.length - 1));
      return true;
    } catch (reason) {
      setAttempt((current) => current ? { ...current, answers: { ...current.answers, [questionId]: previous ?? { questionId, selectedOptionIds: [], state: 'NOT_VISITED', clientSeq: 0 } } } : current);
      setError(reason instanceof ApiError ? reason.message : 'Unable to save this answer.');
      return false;
    } finally {
      setPending(false);
    }
  }, [attempt, examId, pending]);

  useEffect(() => {
    if (!currentQuestion || currentAnswer?.state !== 'NOT_VISITED' || visited.current.has(currentQuestion.id)) return;
    visited.current.add(currentQuestion.id);
    void sendAction('VISIT_QUESTION', currentQuestion.id, selected);
  }, [currentAnswer?.state, currentQuestion, selected, sendAction]);

  const statusCounts = useMemo(() => Object.values(attempt?.answers ?? {}).reduce<Record<string, number>>((counts, answer) => {
    counts[answer.state] = (counts[answer.state] ?? 0) + 1;
    return counts;
  }, {}), [attempt]);

  if (error && !attempt) return <div className="submission-screen"><X size={30} /><h1>Unable to start examination</h1><p className="lead">{error}</p><Link className="button button-secondary" to="/exams">Return to examinations</Link></div>;
  if (!attempt || !currentQuestion) return <div className="submission-screen"><Clock3 size={30} /><p className="muted">Loading the server-authoritative examination paper…</p></div>;
  if (receipt || attempt.status !== 'IN_PROGRESS') return <div className="submission-screen"><div className="success-mark"><CheckCircle2 size={30} /></div><div className="eyebrow">Submission recorded</div><h1>Your examination is {attempt.status === 'EXPIRED' ? 'closed' : 'submitted'}.</h1><p className="lead">Your answers were accepted by the server. Results remain hidden until your instructor publishes them.</p><div className="brand-proof"><ShieldCheck size={18} /><span>{receipt?.submissionId ? `Submission ID: ${receipt.submissionId}` : 'Submission state confirmed by the server.'}</span></div><Link className="button button-secondary" to="/dashboard"><Link2 size={16} />Return to dashboard</Link></div>;

  const saveNext = async () => { await sendAction('SAVE_AND_NEXT', currentQuestion.id, selected, currentIndex < attempt.questions.length - 1); };
  const markReview = () => void sendAction('MARK_REVIEW_AND_NEXT', currentQuestion.id, selected, currentIndex < attempt.questions.length - 1);
  const clear = () => { setDraftSelections((current) => ({ ...current, [currentQuestion.id]: [] })); void sendAction('CLEAR_RESPONSE', currentQuestion.id, []); };

  const notVisitedCount = statusCounts.NOT_VISITED ?? 0;

  return <div className="exam-shell">
    <header className="exam-topbar"><div className="logo">assessly</div><div className="exam-title"><strong>Live examination</strong><span>Question {currentIndex + 1} of {attempt.questions.length}</span></div><div className="exam-clock"><Clock3 size={16} /><strong>{formatRemaining(remaining)}</strong><span>Server-synchronized</span></div><span className="badge green"><span className="live-dot" />Autosave active</span></header>
    <main className="exam-body">
      <ExamQuestionPanel question={currentQuestion} questionIndex={currentIndex} total={attempt.questions.length} selected={selected} pending={pending} error={error} onChoose={(optionId) => setDraftSelections((current) => ({ ...current, [currentQuestion.id]: currentQuestion.type === 'MCQ_SINGLE' ? [optionId] : current[currentQuestion.id]?.includes(optionId) ? current[currentQuestion.id].filter((id) => id !== optionId) : [...(current[currentQuestion.id] ?? []), optionId] }))} onPrevious={() => setCurrentIndex((current) => Math.max(0, current - 1))} onClear={clear} onReview={markReview} onSaveNext={() => void saveNext()} previousDisabled={currentIndex === 0 || pending} lastQuestion={currentIndex === attempt.questions.length - 1} />
      <aside className="exam-sidebar"><div><div className="eyebrow">Navigation</div><h2>Question palette</h2></div><div className="palette-grid">{attempt.questions.map((question, index) => { const state = attempt.answers[question.id]?.state ?? 'NOT_VISITED'; return <button type="button" key={question.id} className={`palette-item ${state.toLowerCase()} ${index === currentIndex ? 'current' : ''}`} onClick={() => setCurrentIndex(index)} aria-label={`Question ${index + 1}, ${state.replaceAll('_', ' ').toLowerCase()}`}>{index + 1}</button>; })}</div><div className="palette-key"><span><i className="dot green" />Answered ({statusCounts.ANSWERED ?? 0})</span><span><i className="dot amber" />Review ({(statusCounts.MARKED_FOR_REVIEW ?? 0) + (statusCounts.ANSWERED_AND_MARKED_FOR_REVIEW ?? 0)})</span><span><i className="dot red" />Not answered ({statusCounts.NOT_ANSWERED ?? 0})</span><span><i className="dot slate" />Not visited ({notVisitedCount})</span></div><div className="exam-submit"><div><strong>Ready to finish?</strong><span className="muted">Review your palette before submitting.</span></div><button type="button" className="button button-primary" disabled={pending} onClick={() => { if (window.confirm('Submit your examination now? You will not be able to change your answers.')) void submit(); }}><Send size={16} />Final submit</button></div></aside>
    </main>
  </div>;
}
