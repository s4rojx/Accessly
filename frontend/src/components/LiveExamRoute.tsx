import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Check, Circle, Clock3, Flag, Send } from 'lucide-react';
import { mockApi, seedExams, statusLabel } from '../services/mock';
import type { Attempt, AttemptAnswer, Exam, HeatmapState } from '../types';
import { ExamQuestionPanel } from './ExamQuestionPanel';

function ExamPalette({ answers, questions, current, onSelect }: { answers: Record<string, AttemptAnswer>; questions: Exam['questions']; current: number; onSelect: (index: number) => void }) {
  return <div className="palette"><div className="section-heading"><h3>Question palette</h3><span className="muted">{questions.length} total</span></div><div className="palette-grid">{questions.map((question, index) => { const state = answers[question.id]?.state ?? 'NOT_VISITED'; return <button key={question.id} aria-label={`Question ${index + 1}: ${statusLabel[state]}`} className={`palette-item ${state.toLowerCase()} ${index === current ? 'current' : ''}`} onClick={() => onSelect(index)}><span>{index + 1}</span><small>{state === 'ANSWERED' || state === 'ANSWERED_AND_MARKED_FOR_REVIEW' ? <Check size={11} /> : state.includes('REVIEW') ? <Flag size={11} /> : state === 'NOT_ANSWERED' ? <Circle size={9} /> : ''}</small></button>; })}</div><div className="palette-key"><span><i className="dot green" />Answered</span><span><i className="dot amber" />Review</span><span><i className="dot slate" />Not visited</span></div></div>;
}

function SubmitDialog({ onClose, onSubmit, answers }: { onClose: () => void; onSubmit: () => void; answers: Record<string, AttemptAnswer> }) {
  const states: HeatmapState[] = ['ANSWERED', 'NOT_ANSWERED', 'MARKED_FOR_REVIEW', 'ANSWERED_AND_MARKED_FOR_REVIEW', 'NOT_VISITED'];
  return <div className="modal-backdrop" role="presentation"><div className="modal" role="dialog" aria-modal="true" aria-label="Submit examination"><button className="modal-close" onClick={onClose}>×</button><h2>Submit examination?</h2><p className="muted">Once accepted, your attempt will be locked. Review the current state summary before continuing.</p><div className="summary-grid">{states.map((state) => <div key={state}><strong>{Object.values(answers).filter((answer) => answer.state === state).length}</strong><span>{statusLabel[state]}</span></div>)}</div><div className="modal-actions"><button className="button button-secondary" onClick={onClose}>Keep working</button><button className="button button-danger" onClick={onSubmit}>Confirm submission <Send size={15} /></button></div></div></div>;
}

export function LiveExamRoute() {
  const { examId } = useParams();
  const exam = seedExams.find((item) => item.id === examId) ?? seedExams[0];
  const [attempt, setAttempt] = useState<Attempt | null>(null);
  const [current, setCurrent] = useState(0);
  const [selected, setSelected] = useState<string[]>([]);
  const [submitOpen, setSubmitOpen] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [remaining, setRemaining] = useState(3600);
  const question = exam.questions[current];
  const answer = attempt?.answers[question.id];

  useEffect(() => { mockApi.startAttempt(exam.id).then(setAttempt); }, [exam.id]);
  useEffect(() => { if (!attempt || submitted) return; const timer = window.setInterval(() => setRemaining((value) => Math.max(0, value - 1)), 1000); return () => window.clearInterval(timer); }, [attempt, submitted]);
  useEffect(() => { setSelected(answer?.selectedOptionIds ?? []); }, [answer?.selectedOptionIds, question.id]);

  const choose = (id: string) => setSelected((old) => question.type === 'MCQ_SINGLE' ? [id] : old.includes(id) ? old.filter((value) => value !== id) : [...old, id]);
  const save = async (state: HeatmapState, advance: boolean) => {
    if (!attempt) return;
    const next: AttemptAnswer = { questionId: question.id, selectedOptionIds: selected, state, clientSeq: (answer?.clientSeq ?? 0) + 1, pending: true };
    setAttempt({ ...attempt, answers: { ...attempt.answers, [question.id]: next } });
    try {
      const saved = await mockApi.action(exam.id, next);
      setAttempt((value) => value ? { ...value, answers: { ...value.answers, [question.id]: saved } } : value);
      if (advance && current < exam.questions.length - 1) setCurrent((value) => value + 1);
    } catch {
      setAttempt((value) => value ? { ...value, answers: { ...value.answers, [question.id]: { ...next, pending: false, error: 'Save failed. Retry.' } } } : value);
    }
  };
  const submit = async () => { const receipt = await mockApi.submit(exam.id); setAttempt((value) => value ? { ...value, submitted: true, submissionReceipt: receipt } : value); setSubmitted(true); setSubmitOpen(false); };
  if (submitted) return <div className="submission-screen"><div className="success-mark"><Check size={30} /></div><div className="eyebrow">Submission received</div><h1>Your examination is safely in the queue.</h1><p className="lead">Your submission receipt is <strong>{attempt?.submissionReceipt?.submissionId}</strong>. Grading is asynchronous and results will appear after publication.</p><Link className="button button-primary" to="/dashboard">Return to dashboard</Link></div>;
  return <div className="exam-shell"><header className="exam-topbar"><Link className="logo" to="/dashboard"><span className="logo-mark">A</span><span>assessly</span></Link><div className="exam-title"><strong>{exam.title}</strong><span>{current + 1} of {exam.questions.length}</span></div><div className="exam-clock"><Clock3 size={17} /><strong>{String(Math.floor(remaining / 60)).padStart(2, '0')}:{String(remaining % 60).padStart(2, '0')}</strong><span>Server-synchronized</span></div><span className="badge badge-green"><span className="live-dot" />Autosave ready</span></header><div className="exam-body"><main><ExamQuestionPanel question={question} questionIndex={current} total={exam.questions.length} selected={selected} pending={answer?.pending} error={answer?.error} onChoose={choose} onPrevious={() => setCurrent((value) => value - 1)} onClear={() => { setSelected([]); void save('NOT_ANSWERED', false); }} onReview={() => void save(selected.length ? 'ANSWERED_AND_MARKED_FOR_REVIEW' : 'MARKED_FOR_REVIEW', false)} onSaveNext={() => void save(selected.length ? 'ANSWERED' : 'NOT_ANSWERED', true)} previousDisabled={current === 0} lastQuestion={current === exam.questions.length - 1} /></main><aside className="exam-sidebar"><ExamPalette answers={attempt?.answers ?? {}} questions={exam.questions} current={current} onSelect={setCurrent} /><div className="exam-submit"><div><strong>Ready to finish?</strong><span className="muted">Review your palette before submitting.</span></div><button className="button button-danger" onClick={() => setSubmitOpen(true)}><Send size={15} />Final submit</button></div></aside></div>{submitOpen && <SubmitDialog onClose={() => setSubmitOpen(false)} onSubmit={() => void submit()} answers={attempt?.answers ?? {}} />}</div>;
}
