import { Clock3, Link2, ShieldCheck } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';

export function LiveExamRoute() {
  const { examId } = useParams();
  return <div className="submission-screen"><div className="success-mark"><Clock3 size={30} /></div><div className="eyebrow">Phase 3 boundary</div><h1>Live examination engine is not enabled yet.</h1><p className="lead">Exam <strong>{examId}</strong> is available through the real candidate API, but no attempt was started. The server-authoritative attempt lifecycle will be added in Phase 3.</p><div className="brand-proof"><ShieldCheck size={18} /><span>No simulated timer, answers, or submission was created.</span></div><Link className="button button-secondary" to="/exams"><Link2 size={16} />Return to examinations</Link></div>;
}
