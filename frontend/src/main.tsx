import { StrictMode, useEffect, useState, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight, BarChart3, Bell, BookOpen, CalendarDays, Check, CheckCircle2, ChevronDown, Clock3, FileQuestion, HelpCircle, LayoutDashboard, LogOut, Menu, Moon, Plus, Save, Send, ShieldCheck, Sun, Users, X } from 'lucide-react';
import { api, ApiError, getCurrentUser, type ExamInput } from './services/api';
import type { Exam, Question, Role } from './types';
import './styles.css';
import './refinement.css';
import { LiveExamRoute } from './components/LiveExamRoute';

function useTheme() {
  const [dark, setDark] = useState(() => localStorage.getItem('assessly-theme') === 'dark');
  useEffect(() => { document.documentElement.dataset.theme = dark ? 'dark' : 'light'; localStorage.setItem('assessly-theme', dark ? 'dark' : 'light'); }, [dark]);
  return [dark, () => setDark((value) => !value)] as const;
}

function Logo() { return <Link className="logo" to="/dashboard"><span className="logo-mark">A</span><span>assessly</span></Link>; }
function Button({ children, variant = 'primary', icon, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'ghost' | 'danger'; icon?: ReactNode }) { return <button className={`button button-${variant}`} {...props}>{icon}{children}</button>; }
function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: string }) { return <span className={`badge badge-${tone}`}>{children}</span>; }
function ThemeButton() { const [dark, toggle] = useTheme(); return <button className="icon-button" aria-label="Toggle theme" onClick={toggle}>{dark ? <Sun size={17} /> : <Moon size={17} />}</button>; }

function AppShell({ role }: { role: Role }) {
  const user = getCurrentUser();
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const nav = role === 'ROLE_ADMIN'
    ? [{ to: '/dashboard', label: 'Overview', icon: LayoutDashboard }, { to: '/teacher/exams', label: 'Examinations', icon: FileQuestion }, { to: '/teacher/exams/create', label: 'Create exam', icon: Plus }]
    : [{ to: '/dashboard', label: 'Overview', icon: LayoutDashboard }, { to: '/exams', label: 'My examinations', icon: BookOpen }, { to: '/results', label: 'Results', icon: BarChart3 }];
  return <div className="app-frame"><aside className={open ? 'sidebar open' : 'sidebar'}><div className="sidebar-top"><Logo /><button className="icon-button mobile-only" onClick={() => setOpen(false)}><X size={18} /></button></div><nav>{nav.map(({ to, label, icon: Icon }) => <Link key={to} className={location.pathname === to ? 'nav-link active' : 'nav-link'} to={to} onClick={() => setOpen(false)}><Icon size={18} />{label}</Link>)}</nav><div className="sidebar-footer"><div className="mini-note"><ShieldCheck size={17} /><span>Assessment integrity<br /><small>Server-authoritative workflows</small></span></div><button className="nav-link" onClick={async () => { await api.logout(); navigate('/auth/login'); }}><LogOut size={18} />Sign out</button></div></aside><main className="main"><header className="topbar"><button className="icon-button mobile-only" onClick={() => setOpen(true)}><Menu size={19} /></button><div className="crumb">{role === 'ROLE_ADMIN' ? 'Teacher workspace' : 'Candidate workspace'}</div><div className="top-actions"><button className="icon-button" aria-label="Notifications"><Bell size={18} /></button><ThemeButton /><div className="user-menu"><div className="avatar">{user?.fullName.slice(0, 1) ?? 'A'}</div><span className="hide-mobile">{user?.fullName ?? 'Guest'}</span><ChevronDown size={15} /></div></div></header><div className="content"><Outlet /></div></main></div>;
}

function PublicShell() { return <div className="public-shell"><div className="public-brand"><Logo /><div className="public-brand-copy"><Badge tone="blue">Academic assessment platform</Badge><h1>Make every examination<br /><em>feel considered.</em></h1><p>Assessly gives serious academic teams a calm, reliable space to create, run, and understand assessments.</p><div className="brand-proof"><ShieldCheck size={18} /><span>Designed around transparent assessment state and server-authoritative timing.</span></div></div></div><div className="public-panel"><ThemeButton /><Outlet /></div></div>; }
function Protected({ role }: { role?: Role }) { const user = getCurrentUser(); if (!user) return <Navigate to="/auth/login" replace />; if (role && user.role !== role) return <Navigate to="/unauthorized" replace />; return <AppShell role={user.role} />; }

function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const submit = async (event: React.FormEvent) => { event.preventDefault(); setLoading(true); setError(''); try { await api.login(email, password); navigate('/dashboard'); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to sign in.'); } finally { setLoading(false); } };
  return <div className="auth-card"><div className="eyebrow">Welcome back</div><h2>Sign in to Assessly</h2><p className="muted">Use the account provisioned by your administrator.</p><form onSubmit={submit} className="stack"><label>Email<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label><label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>{error && <div className="alert error">{error}</div>}<Button type="submit" disabled={loading}>{loading ? 'Signing in…' : 'Continue'} <ArrowRight size={17} /></Button></form><p className="auth-foot">New candidate? <Link to="/auth/signup">Create an account</Link></p></div>;
}

function Signup() {
  const navigate = useNavigate();
  const [values, setValues] = useState({ fullName: '', email: '', rollNumber: '', password: '' });
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  const submit = async (event: React.FormEvent) => { event.preventDefault(); setError(''); try { await api.signup(values); setSaved(true); setTimeout(() => navigate('/auth/login'), 700); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to create the account.'); } };
  return <div className="auth-card"><div className="eyebrow">Candidate registration</div><h2>Create your account</h2><p className="muted">Candidate access is granted to assessments through an administrator roster.</p><form onSubmit={submit} className="stack"><label>Full name<input required value={values.fullName} onChange={(event) => setValues({ ...values, fullName: event.target.value })} /></label><label>Email<input required type="email" value={values.email} onChange={(event) => setValues({ ...values, email: event.target.value })} /></label><label>Roll number<input required value={values.rollNumber} onChange={(event) => setValues({ ...values, rollNumber: event.target.value })} /></label><label>Password<input required minLength={8} type="password" value={values.password} onChange={(event) => setValues({ ...values, password: event.target.value })} /></label>{error && <div className="alert error">{error}</div>}{saved && <div className="alert success">Account created. Redirecting to sign in.</div>}<Button type="submit" disabled={saved}>Create candidate account <ArrowRight size={17} /></Button></form><p className="auth-foot">Already registered? <Link to="/auth/login">Sign in</Link></p></div>;
}

function PageHeader({ eyebrow, title, description, action }: { eyebrow?: string; title: string; description?: string; action?: ReactNode }) { return <div className="page-header"><div>{eyebrow && <div className="eyebrow">{eyebrow}</div>}<h1>{title}</h1>{description && <p className="muted">{description}</p>}</div>{action}</div>; }
function LoadingState() { return <div className="skeleton large" aria-label="Loading" />; }
function ErrorState({ error, onRetry }: { error: string; onRetry?: () => void }) { return <div className="empty-state card"><X size={27} /><h2>Unable to load this workspace</h2><p className="muted">{error}</p>{onRetry && <Button variant="secondary" onClick={onRetry}>Try again</Button>}</div>; }

function Calendar({ exams, role }: { exams: Exam[]; role: Role }) {
  return <section className="calendar card"><div className="calendar-header"><div><div className="eyebrow">Schedule</div><h2>Assessment calendar</h2></div><CalendarDays size={22} /></div><div className="agenda">{exams.length === 0 ? <p className="muted">No assessments are currently available.</p> : exams.map((exam) => <Link to={role === 'ROLE_ADMIN' ? `/teacher/exams/${exam.id}` : `/exam/${exam.id}/lobby`} className="agenda-event" key={exam.id}><span><strong>{exam.title}</strong><small>{new Date(exam.start).toLocaleString()} · {exam.durationMinutes} min</small></span><ArrowRight size={16} /></Link>)}</div></section>;
}

function Dashboard() {
  const user = getCurrentUser()!;
  const [exams, setExams] = useState<Exam[] | null>(null);
  const [error, setError] = useState('');
  useEffect(() => { const load = async () => { try { setExams(user.role === 'ROLE_ADMIN' ? await api.listAdminExams() : await api.listExams()); } catch (reason) { setError(reason instanceof Error ? reason.message : 'Unable to load assessments.'); } }; void load(); }, [user.role]);
  if (error) return <ErrorState error={error} />;
  if (!exams) return <LoadingState />;
  return <><PageHeader eyebrow={user.role === 'ROLE_ADMIN' ? 'Teacher workspace' : 'Candidate workspace'} title={user.role === 'ROLE_ADMIN' ? 'Examination overview' : `Welcome back, ${user.fullName.split(' ')[0]}.`} description="A live view of assessment records from the Assessly API." action={user.role === 'ROLE_ADMIN' ? <Link className="button button-primary" to="/teacher/exams/create"><Plus size={17} />Create examination</Link> : <Link className="button button-secondary" to="/exams">View examinations <ArrowRight size={16} /></Link>} /><div className="stats-row"><Stat label="Visible assessments" value={exams.length} icon={CalendarDays} /><Stat label="Published windows" value={exams.filter((exam) => exam.status !== 'DRAFT').length} icon={CheckCircle2} tone="green" /><Stat label="Enrolled candidates" value={exams.reduce((sum, exam) => sum + exam.enrolled, 0)} icon={Users} tone="blue" /></div><Calendar exams={exams} role={user.role} /></>;
}

function Stat({ label, value, icon: Icon, tone = 'blue' }: { label: string; value: ReactNode; icon: typeof Users; tone?: string }) { return <div className="stat-card"><span className={`stat-icon ${tone}`}><Icon size={18} /></span><span><small>{label}</small><strong>{value}</strong></span></div>; }

function Exams() {
  const [exams, setExams] = useState<Exam[] | null>(null);
  const [error, setError] = useState('');
  useEffect(() => { api.listExams().then(setExams).catch((reason) => setError(reason instanceof Error ? reason.message : 'Unable to load examinations.')); }, []);
  if (error) return <ErrorState error={error} />;
  if (!exams) return <LoadingState />;
  return <><PageHeader eyebrow="Candidate workspace" title="My examinations" description="Your rostered assessment windows and publication status." /><div className="exam-list">{exams.length === 0 ? <div className="empty-state card"><BookOpen size={27} /><h2>No examinations assigned</h2><p className="muted">An administrator will add you to an assessment roster when it is ready.</p></div> : exams.map((exam) => <ExamRow exam={exam} key={exam.id} />)}</div></>;
}
function ExamRow({ exam }: { exam: Exam }) {
  const resultAvailable = exam.resultPublished && (exam.attemptStatus === 'SUBMITTED' || exam.attemptStatus === 'EXPIRED');
  return <article className="exam-row card"><div className="exam-row-icon"><BookOpen size={19} /></div><div className="exam-row-main"><div className="exam-row-title"><h3>{exam.title}</h3><Badge tone={exam.status === 'AVAILABLE' ? 'green' : exam.status === 'COMPLETED' ? 'slate' : 'blue'}>{exam.status.replace('_', ' ')}</Badge></div><p className="muted">{exam.subject} · {new Date(exam.start).toLocaleDateString()} · {exam.durationMinutes} min</p><div className="row-meta"><span><FileQuestion size={14} />{exam.questions.length} questions</span><span><BarChart3 size={14} />{exam.maximumMarks} marks</span><span><Clock3 size={14} />Until {new Date(exam.end).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</span></div></div><Link className="button button-secondary" to={resultAvailable ? `/results/${exam.id}` : `/exam/${exam.id}/lobby`}>{resultAvailable ? 'View result' : 'View details'} <ArrowRight size={15} /></Link></article>;
}

function Lobby() {
  const navigate = useNavigate();
  const { examId } = useParams();
  const [exam, setExam] = useState<Exam | null>(null);
  const [error, setError] = useState('');
  const [ack, setAck] = useState(false);
  const [starting, setStarting] = useState(false);
  useEffect(() => { if (examId) api.getExam(examId).then(setExam).catch((reason) => setError(reason instanceof Error ? reason.message : 'Unable to load the examination.')); }, [examId]);
  const start = async () => {
    if (!examId) return;
    setStarting(true);
    setError('');
    try { await api.startAttempt(examId); navigate(`/exam/${examId}/live`); }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to start the examination.'); }
    finally { setStarting(false); }
  };
  if (error) return <ErrorState error={error} />;
  if (!exam) return <LoadingState />;
  return <><Link className="back-link" to="/exams"><ArrowLeft size={15} />Back to examinations</Link><div className="lobby-grid"><section className="card lobby-main"><div className="eyebrow">Examination lobby</div><h1>{exam.title}</h1><p className="lead">{exam.description}</p><div className="lobby-facts"><div><span>Availability</span><strong>{new Date(exam.start).toLocaleString()} – {new Date(exam.end).toLocaleTimeString()}</strong></div><div><span>Duration</span><strong>{exam.durationMinutes} minutes</strong></div><div><span>Assessment</span><strong>{exam.questions.length} questions · {exam.maximumMarks} marks</strong></div></div><div className="rules"><h3>Before you begin</h3><ul><li><Check size={16} />Your attempt timer will be controlled by the assessment server.</li><li><Check size={16} />Answers are saved synchronously by the assessment server.</li><li><Check size={16} />Results become visible only after publication by your instructor.</li></ul></div><label className="ack"><input type="checkbox" checked={ack} onChange={(event) => setAck(event.target.checked)} />I have read and agree to the examination rules.</label>{error && <div className="alert error">{error}</div>}<Button disabled={!ack || starting} onClick={() => void start()}>{starting ? 'Starting…' : 'Start examination'} <ArrowRight size={16} /></Button></section><aside className="card lobby-side"><div className="lobby-status"><span className="status-ring"><CheckCircle2 size={20} /></span><div><strong>{exam.status === 'AVAILABLE' ? 'Available to start' : exam.status}</strong><span className="muted">Your enrollment is confirmed by the server</span></div></div><div className="integrity-box"><ShieldCheck size={18} /><div><strong>Assessment integrity</strong><p className="muted">Candidate responses never include answer keys.</p></div></div></aside></div></>;
}

function TeacherExams() {
  const [exams, setExams] = useState<Exam[] | null>(null);
  const [error, setError] = useState('');
  useEffect(() => { api.listAdminExams().then(setExams).catch((reason) => setError(reason instanceof Error ? reason.message : 'Unable to load examinations.')); }, []);
  if (error) return <ErrorState error={error} />;
  if (!exams) return <LoadingState />;
  return <><PageHeader eyebrow="Teacher workspace" title="Examinations" description="Create focused assessments and control their publication lifecycle." action={<Link className="button button-primary" to="/teacher/exams/create"><Plus size={17} />Create examination</Link>} /><div className="teacher-table card"><div className="table-head"><span>Examination</span><span>Window</span><span>Enrolled</span><span>Status</span><span /></div>{exams.length === 0 ? <div className="empty-state"><p className="muted">No examinations have been created.</p></div> : exams.map((exam) => <Link className="table-row" to={`/teacher/exams/${exam.id}`} key={exam.id}><div><strong>{exam.title}</strong><small>{exam.subject} · {exam.questions.length} questions</small></div><span>{new Date(exam.start).toLocaleDateString()}<small>{new Date(exam.start).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</small></span><span>{exam.enrolled}</span><Badge tone={exam.status === 'PUBLISHED' ? 'green' : 'blue'}>{exam.status}</Badge><ArrowRight size={17} /></Link>)}</div></>;
}

function CreateExam() {
  const navigate = useNavigate();
  const [questions, setQuestions] = useState<Question[] | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [values, setValues] = useState({ title: '', subject: '', description: '', start: '', end: '', duration: '60', shuffleQuestions: true, shuffleOptions: true });
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => { api.listAdminQuestions().then(setQuestions).catch((reason) => setError(reason instanceof Error ? reason.message : 'Unable to load the question bank.')); }, []);
  const save = async () => { setSaving(true); setError(''); const input: ExamInput = { title: values.title, subject: values.subject, description: values.description, windowStartTime: new Date(values.start).toISOString(), windowEndTime: new Date(values.end).toISOString(), durationMinutes: Number(values.duration), questionIds: selected, shuffleQuestions: values.shuffleQuestions, shuffleOptions: values.shuffleOptions }; try { await api.createExam(input); navigate('/teacher/exams'); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to save the examination.'); } finally { setSaving(false); } };
  if (!questions) return error ? <ErrorState error={error} /> : <LoadingState />;
  return <><PageHeader eyebrow="Teacher workspace" title="Create examination" description="Build a draft from your real question bank." action={<Button variant="secondary" onClick={() => navigate('/teacher/exams')}>Cancel</Button>} /><div className="builder-grid"><section className="card form-card"><div className="section-heading"><div><h2>Basic information</h2><p className="muted">The details candidates see before they start.</p></div><Badge tone="blue">Draft</Badge></div><div className="form-grid"><label>Examination title<input value={values.title} onChange={(event) => setValues({ ...values, title: event.target.value })} /></label><label>Subject or category<input value={values.subject} onChange={(event) => setValues({ ...values, subject: event.target.value })} /></label><label className="span-2">Instructions<textarea rows={4} value={values.description} onChange={(event) => setValues({ ...values, description: event.target.value })} /></label></div></section><section className="card form-card"><div className="section-heading"><div><h2>Questions</h2><p className="muted">Select MCQ questions owned by your account.</p></div></div>{questions.map((question) => <label className="question-list-item" key={question.id}><input type="checkbox" checked={selected.includes(question.id)} onChange={(event) => setSelected(event.target.checked ? [...selected, question.id] : selected.filter((id) => id !== question.id))} /><div><strong>{question.prompt}</strong><small>{question.category} · {question.positiveMarks} marks · {question.type === 'MCQ_MULTI' ? 'Multi-select' : 'Single choice'}</small></div></label>)}<div className="unavailable"><HelpCircle size={16} /><span><strong>Coding questions</strong> are unavailable in this release.</span></div></section><section className="card form-card"><div className="section-heading"><div><h2>Schedule and rules</h2><p className="muted">The backend validates the window and derived marks.</p></div></div><div className="form-grid"><label>Availability start<input type="datetime-local" value={values.start} onChange={(event) => setValues({ ...values, start: event.target.value })} /></label><label>Availability end<input type="datetime-local" value={values.end} onChange={(event) => setValues({ ...values, end: event.target.value })} /></label><label>Duration (minutes)<input type="number" min="1" value={values.duration} onChange={(event) => setValues({ ...values, duration: event.target.value })} /></label></div><div className="toggle-row"><label><input type="checkbox" checked={values.shuffleQuestions} onChange={(event) => setValues({ ...values, shuffleQuestions: event.target.checked })} /> Shuffle question order</label><label><input type="checkbox" checked={values.shuffleOptions} onChange={(event) => setValues({ ...values, shuffleOptions: event.target.checked })} /> Shuffle option order</label></div>{error && <div className="alert error">{error}</div>}<div className="builder-actions"><Button variant="secondary" onClick={() => navigate('/teacher/exams')}>Cancel</Button><Button disabled={saving || !values.title || !values.subject || !values.start || !values.end} onClick={() => void save()}>{saving ? 'Saving…' : 'Save draft'} <Save size={15} /></Button></div></section></div></>;
}

function TeacherDetail() {
  const { examId } = useParams();
  const [exam, setExam] = useState<Exam | null>(null);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [publishingResults, setPublishingResults] = useState(false);
  useEffect(() => { if (examId) api.getAdminExam(examId).then(setExam).catch((reason) => setError(reason instanceof Error ? reason.message : 'Unable to load the examination.')); }, [examId]);
  if (error) return <ErrorState error={error} />;
  if (!exam) return <LoadingState />;
  const publish = async () => { setSaving(true); setError(''); try { setExam(await api.publishExam(exam.id)); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to publish the examination.'); } finally { setSaving(false); } };
  const publishResults = async () => { setPublishingResults(true); setError(''); try { await api.publishResults(exam.id); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to publish results.'); } finally { setPublishingResults(false); } };
  return <><Link className="back-link" to="/teacher/exams"><ArrowLeft size={15} />Back to examinations</Link><PageHeader eyebrow="Examination detail" title={exam.title} description={`${exam.subject} · ${exam.questions.length} questions · ${exam.durationMinutes} minutes`} action={<Badge tone={exam.status === 'PUBLISHED' ? 'green' : 'blue'}>{exam.status}</Badge>} /><div className="stats-row"><Stat label="Enrolled" value={exam.enrolled} icon={Users} /><Stat label="Questions" value={exam.questions.length} icon={FileQuestion} tone="blue" /><Stat label="Maximum marks" value={exam.maximumMarks} icon={BarChart3} tone="green" /></div><section className="card publish-card"><div className="eyebrow">Publication control</div><h2>{exam.status === 'PUBLISHED' ? 'Exam is published' : 'Draft examination'}</h2><p className="muted">Publishing locks the schedule and question selection for this assessment.</p>{error && <div className="alert error">{error}</div>}<div className="builder-actions"><Button disabled={saving || exam.status === 'PUBLISHED'} onClick={() => void publish()}>{saving ? 'Publishing…' : exam.status === 'PUBLISHED' ? 'Published' : 'Publish examination'} <Send size={15} /></Button><Button variant="secondary" disabled={publishingResults || exam.status !== 'PUBLISHED'} onClick={() => void publishResults()}>{publishingResults ? 'Publishing results…' : 'Publish results'} <Send size={15} /></Button></div></section></>;
}

function Results() {
  const { examId } = useParams();
  const [result, setResult] = useState<Awaited<ReturnType<typeof api.getResult>> | null>(null);
  const [publishedExams, setPublishedExams] = useState<Exam[] | null>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    setError('');
    if (examId) {
      setPublishedExams(null);
      api.getResult(examId).then(setResult).catch((reason) => setError(reason instanceof ApiError ? reason.message : 'Unable to load the result.'));
      return;
    }
    setResult(null);
    api.listExams()
      .then((exams) => setPublishedExams(exams.filter((exam) => exam.resultPublished && (exam.attemptStatus === 'SUBMITTED' || exam.attemptStatus === 'EXPIRED'))))
      .catch((reason) => setError(reason instanceof ApiError ? reason.message : 'Unable to load published results.'));
  }, [examId]);
  if (error) return <ErrorState error={error} />;
  if (!examId) {
    if (!publishedExams) return <LoadingState />;
    return <><PageHeader eyebrow="Candidate workspace" title="Results" description="Published results from your completed examinations." /><div className="exam-list">{publishedExams.length === 0 ? <div className="empty-state card"><BarChart3 size={27} /><h2>No published results</h2><p className="muted">Your instructor has not published a result for a completed examination yet.</p></div> : publishedExams.map((exam) => <article className="exam-row card" key={exam.id}><div className="exam-row-icon"><BarChart3 size={19} /></div><div className="exam-row-main"><h3>{exam.title}</h3><p className="muted">{exam.subject} · {exam.questions.length} questions · {exam.maximumMarks} marks</p></div><Link className="button button-secondary" to={`/results/${exam.id}`}>View result <ArrowRight size={15} /></Link></article>)}</div></>;
  }
  if (!result) return <LoadingState />;
  return <><PageHeader eyebrow="Candidate workspace" title="Examination result" description="Published result generated from your submitted answers." /><section className="result-hero card"><div><span className="eyebrow">Total score</span><strong>{result.totalMarks}<small> / {result.maximumMarks}</small></strong><span className="muted">Submitted {new Date(result.submittedAt).toLocaleString()}</span></div><div className="score-ring">{result.percentage}%</div></section><section className="result-detail card"><div className="stats-row"><Stat label="Correct" value={result.correct} icon={CheckCircle2} tone="green" /><Stat label="Incorrect" value={result.incorrect} icon={X} tone="red" /><Stat label="Unattempted" value={result.unattempted} icon={Clock3} tone="blue" /></div><Link className="button button-secondary" to="/dashboard">Return to dashboard</Link></section></>;
}
function Unauthorized() { return <div className="empty-state card"><ShieldCheck size={30} /><h1>Access restricted</h1><p className="muted">This workspace is not available for your role.</p><Link to="/dashboard" className="button button-primary">Return to dashboard</Link></div>; }

function App() { return <Routes><Route element={<PublicShell />}><Route path="/" element={<Navigate to="/auth/login" replace />} /><Route path="/auth/login" element={<Login />} /><Route path="/auth/signup" element={<Signup />} /></Route><Route element={<Protected />}><Route path="/dashboard" element={<Dashboard />} /><Route path="/exams" element={<Exams />} /><Route path="/exam/:examId/lobby" element={<Lobby />} /><Route path="/exam/:examId/live" element={<LiveExamRoute />} /><Route path="/results" element={<Results />} /><Route path="/results/:examId" element={<Results />} /></Route><Route element={<Protected role="ROLE_ADMIN" />}><Route path="/teacher/exams" element={<TeacherExams />} /><Route path="/teacher/exams/create" element={<CreateExam />} /><Route path="/teacher/exams/:examId" element={<TeacherDetail />} /></Route><Route path="/unauthorized" element={<Unauthorized />} /><Route path="*" element={<Navigate to="/dashboard" replace />} /></Routes>; }

createRoot(document.getElementById('root')!).render(<StrictMode><BrowserRouter><App /></BrowserRouter></StrictMode>);
