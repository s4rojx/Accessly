import type { Question } from '../types';
import { ArrowLeft, Flag, Save, X } from 'lucide-react';

interface ExamQuestionPanelProps {
  question: Question;
  questionIndex: number;
  total: number;
  selected: string[];
  pending?: boolean;
  error?: string;
  onChoose: (optionId: string) => void;
  onPrevious: () => void;
  onClear: () => void;
  onReview: () => void;
  onSaveNext: () => void;
  previousDisabled?: boolean;
  lastQuestion?: boolean;
}

export function ExamQuestionPanel({ question, questionIndex, total, selected, pending, error, onChoose, onPrevious, onClear, onReview, onSaveNext, previousDisabled, lastQuestion }: ExamQuestionPanelProps) {
  const multi = question.type === 'MCQ_MULTI';
  return (
    <section className="questionnaire-panel" aria-labelledby="question-title">
      <div className="questionnaire-progress">Question {questionIndex + 1} of {total}</div>
      <h1 id="question-title" className="questionnaire-title">{question.prompt}</h1>
      <p className="questionnaire-description">{multi ? 'Select all answers that apply.' : 'Select one answer.'}</p>

      <div className="questionnaire-choices" role={multi ? 'group' : 'radiogroup'} aria-label={multi ? 'Answer choices, select all that apply' : 'Answer choices, select one'}>
        {question.options.map((option) => {
          const checked = selected.includes(option.id);
          return (
            <button key={option.id} type="button" className={`questionnaire-choice ${checked ? 'selected' : ''}`} aria-pressed={checked} onClick={() => onChoose(option.id)}>
              <span className={`questionnaire-indicator ${multi ? 'checkbox' : ''}`} aria-hidden="true" />
              <span>{option.text}</span>
            </button>
          );
        })}
      </div>

      <div className="questionnaire-status" aria-live="polite">
        {pending && <span>Saving answer…</span>}
        {error && <span className="questionnaire-error">{error}</span>}
      </div>
      <div className="questionnaire-actions">
        <button type="button" className="button button-secondary" disabled={previousDisabled} onClick={onPrevious}><ArrowLeft size={16} />Previous</button>
        <div className="questionnaire-actions-right">
          <button type="button" className="button button-ghost" onClick={onClear}><X size={16} />Clear</button>
          <button type="button" className="button button-secondary" onClick={onReview}><Flag size={16} />Mark for review</button>
          <button type="button" className="button button-primary" onClick={onSaveNext}><Save size={16} />{lastQuestion ? 'Save answer' : 'Save & next'}</button>
        </div>
      </div>
    </section>
  );
}
