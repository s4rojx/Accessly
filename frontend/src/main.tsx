import { StrictMode, useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight, BarChart3, Bell, BookOpen, CalendarDays, Check, CheckCircle2, ChevronDown, Clock3, FileQuestion, HelpCircle, LayoutDashboard, LogOut, Menu, Moon, PanelLeftClose, PanelLeftOpen, Plus, Save, Send, Settings2, ShieldCheck, Sun, Trash2, Upload, UserRound, Users, X } from 'lucide-react';
import { api, ApiError, getCurrentUser, type ExamInput, type QuestionInput } from './services/api';
import type { AdminCandidate, AdminResult, Exam, ExamAnalytics, Question, Role } from './types';
import './styles.css';
import './refinement.css';
import { LiveExamRoute } from './components/LiveExamRoute';

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (options: { client_id: string; callback: (response: { credential: string }) => void }) => void;
          renderButton: (element: HTMLElement, options: { theme: string; size: string; width: number }) => void;
        };
      };
    };
  }
}

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
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem('assessly-sidebar') === 'collapsed');
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const location = useLocation();
  const nav = role === 'ROLE_ADMIN'
    ? [{ to: '/dashboard', label: 'Overview', icon: LayoutDashboard }, { to: '/teacher/questions', label: 'Question bank', icon: HelpCircle }, { to: '/teacher/exams', label: 'Examinations', icon: FileQuestion }, { to: '/teacher/exams/create', label: 'Create exam', icon: Plus }]
    : [{ to: '/dashboard', label: 'Overview', icon: LayoutDashboard }, { to: '/exams', label: 'My examinations', icon: BookOpen }, { to: '/results', label: 'Results', icon: BarChart3 }];
  const signOut = async () => { await api.logout(); navigate('/auth/login'); };
  useEffect(() => {
    if (!userMenuOpen) return;
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (userMenuRef.current && event.target instanceof Node && !userMenuRef.current.contains(event.target)) setUserMenuOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => { if (event.key === 'Escape') setUserMenuOpen(false); };
    document.addEventListener('mousedown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => { document.removeEventListener('mousedown', closeOnOutsideClick); document.removeEventListener('keydown', closeOnEscape); };
  }, [userMenuOpen]);
  const toggleSidebar = () => setCollapsed((value) => { const next = !value; localStorage.setItem('assessly-sidebar', next ? 'collapsed' : 'expanded'); return next; });
  return <div className="app-frame"><aside className={`sidebar${open ? ' open' : ''}${collapsed ? ' collapsed' : ''}`}><div className="sidebar-top"><Logo /><div className="sidebar-controls"><button type="button" className="icon-button sidebar-toggle" aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} aria-expanded={!collapsed} onClick={toggleSidebar}>{collapsed ? <PanelLeftOpen size={17} /> : <PanelLeftClose size={17} />}</button><button type="button" className="icon-button mobile-only" aria-label="Close navigation" onClick={() => setOpen(false)}><X size={18} /></button></div></div><nav>{nav.map(({ to, label, icon: Icon }) => <Link key={to} title={label} className={location.pathname === to ? 'nav-link active' : 'nav-link'} to={to} onClick={() => setOpen(false)}><Icon size={18} /><span>{label}</span></Link>)}</nav><div className="sidebar-footer"><button type="button" className="nav-link" onClick={() => void signOut()}><LogOut size={18} /><span>Sign out</span></button></div></aside><main className="main"><header className="topbar"><button type="button" className="icon-button mobile-only" aria-label="Open navigation" onClick={() => setOpen(true)}><Menu size={19} /></button><div className="crumb workspace-title">{role === 'ROLE_ADMIN' ? 'Teacher workspace' : 'Candidate workspace'}</div><div className="top-actions"><button type="button" className="icon-button" aria-label="Notifications"><Bell size={18} /></button><ThemeButton /><div ref={userMenuRef} className="user-menu-wrap"><button type="button" className="user-menu" aria-expanded={userMenuOpen} aria-haspopup="menu" onClick={() => setUserMenuOpen((value) => !value)}><span className="avatar">{user?.avatarUrl ? <img src={user.avatarUrl} alt="" /> : user?.fullName.slice(0, 1) ?? 'A'}</span><span className="hide-mobile">{user?.fullName ?? 'Guest'}</span><ChevronDown size={15} /></button>{userMenuOpen && <div className="user-popover" role="menu"><Link className="user-popover-action" role="menuitem" to="/profile" onClick={() => setUserMenuOpen(false)}><UserRound size={15} />Profile</Link><Link className="user-popover-action" role="menuitem" to="/profile#settings" onClick={() => setUserMenuOpen(false)}><Settings2 size={15} />Account settings</Link><div className="user-popover-divider" /><button type="button" className="user-popover-action danger" role="menuitem" onClick={() => void signOut()}><LogOut size={15} />Sign out</button></div>}</div></div></header><div className="content"><Outlet /></div></main></div>;
}

function PublicShell() { return <div className="public-shell"><div className="public-brand"><Logo /><div className="public-brand-copy"><div className="public-brand-kicker">ASSESSLY / ASSESSMENT OS</div><h1>Bring clarity<br /><em>to every assessment.</em></h1><p>Design focused examinations, keep every decision visible, and give candidates a calmer way to perform.</p></div><div className="public-brand-orbit" aria-hidden="true"><span /><span /><span /></div></div><div className="public-panel"><ThemeButton /><Outlet /></div></div>; }
function Protected({ role }: { role?: Role }) { const user = getCurrentUser(); if (!user) return <Navigate to="/auth/login" replace />; if (role && user.role !== role) return <Navigate to="/unauthorized" replace />; return <AppShell role={user.role} />; }

function GoogleSignInButton({ onCredential }: { onCredential: (credential: string) => void }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const callbackRef = useRef(onCredential);
  const [rendered, setRendered] = useState(false);
  callbackRef.current = onCredential;
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined;

  useEffect(() => {
    if (!clientId) return;
    const render = () => {
      if (!containerRef.current || !window.google) return;
      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: (response) => callbackRef.current(response.credential),
      });
      containerRef.current.replaceChildren();
      window.google.accounts.id.renderButton(containerRef.current, { theme: 'outline', size: 'large', width: Math.floor(containerRef.current.clientWidth) });
      setRendered(true);
    };
    if (window.google) {
      render();
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.addEventListener('load', render);
    document.head.appendChild(script);
    return () => script.removeEventListener('load', render);
  }, [clientId]);

  if (!clientId) return <button type="button" className="google-signin-disabled" disabled>Google sign-in is not configured</button>;
  return <div className="google-signin"><div ref={containerRef} />{!rendered && <span className="muted">Loading Google sign-in…</span>}</div>;
}

function AuthDivider() { return <div className="auth-divider"><span>or</span></div>; }

function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const submit = async (event: React.FormEvent) => { event.preventDefault(); setLoading(true); setError(''); try { await api.login(email, password); navigate('/dashboard'); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to sign in.'); } finally { setLoading(false); } };
  const googleLogin = async (credential: string) => { setGoogleLoading(true); setError(''); try { await api.loginWithGoogle(credential); navigate('/dashboard'); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to sign in with Google.'); } finally { setGoogleLoading(false); } };
  return <div className="auth-card"><div className="eyebrow">Welcome back</div><h2>Sign in to Assessly</h2><form onSubmit={submit} className="stack"><label>Email<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label><label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>{error && <div className="alert error">{error}</div>}<Button type="submit" disabled={loading || googleLoading}>{loading ? 'Signing in…' : 'Continue'} <ArrowRight size={17} /></Button></form><AuthDivider /><GoogleSignInButton onCredential={(credential) => void googleLogin(credential)} /><p className="auth-foot">New candidate? <Link to="/auth/signup">Create an account</Link></p><p className="auth-foot"><Link to="/admin/login">Administrator portal</Link></p></div>;
}

function Signup() {
  const navigate = useNavigate();
  const [values, setValues] = useState({ fullName: '', email: '', rollNumber: '', password: '' });
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const submit = async (event: React.FormEvent) => { event.preventDefault(); setError(''); try { await api.signup(values); setSaved(true); setTimeout(() => navigate('/auth/login'), 700); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to create the account.'); } };
  const googleSignup = async (credential: string) => { setGoogleLoading(true); setError(''); try { await api.loginWithGoogle(credential); navigate('/dashboard'); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to continue with Google.'); } finally { setGoogleLoading(false); } };
  return <div className="auth-card"><div className="eyebrow">Candidate registration</div><h2>Create your account</h2><form onSubmit={submit} className="stack"><label>Full name<input required value={values.fullName} onChange={(event) => setValues({ ...values, fullName: event.target.value })} /></label><label>Email<input required type="email" value={values.email} onChange={(event) => setValues({ ...values, email: event.target.value })} /></label><label>Roll number<input required value={values.rollNumber} onChange={(event) => setValues({ ...values, rollNumber: event.target.value })} /></label><label>Password<input required minLength={8} type="password" value={values.password} onChange={(event) => setValues({ ...values, password: event.target.value })} /></label>{error && <div className="alert error">{error}</div>}{saved && <div className="alert success">Account created. Redirecting to sign in.</div>}<Button type="submit" disabled={saved || googleLoading}>Create candidate account <ArrowRight size={17} /></Button></form><AuthDivider /><GoogleSignInButton onCredential={(credential) => void googleSignup(credential)} /><p className="auth-foot">Already registered? <Link to="/auth/login">Sign in</Link></p></div>;
}

function AdminLogin() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const submit = async (event: React.FormEvent) => { event.preventDefault(); setLoading(true); setError(''); try { await api.login(email, password, 'admin'); navigate('/teacher/exams'); } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to sign in.'); } finally { setLoading(false); } };
  return <div className="auth-card admin-auth-card"><div className="eyebrow">Secure access</div><h2>Assessly Administrator Portal</h2><p className="muted">Sign in to manage assessments, question banks, and candidate rosters.</p><div className="alert info">Administrator accounts are securely provisioned via system bootstrap. Contact your platform administrator for access.</div><form onSubmit={submit} className="stack"><label>Email<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label><label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>{error && <div className="alert error">{error}</div>}<Button type="submit" disabled={loading}>{loading ? 'Signing in…' : 'Enter administrator workspace'} <ArrowRight size={17} /></Button></form><p className="auth-foot"><Link to="/auth/login">Return to candidate sign in</Link></p></div>;
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
  return <><PageHeader eyebrow={user.role === 'ROLE_ADMIN' ? 'Teacher workspace' : 'Candidate workspace'} title={user.role === 'ROLE_ADMIN' ? 'Examination overview' : `Welcome back, ${user.fullName.split(' ')[0]}.`} action={user.role === 'ROLE_ADMIN' ? <Link className="button button-primary" to="/teacher/exams/create"><Plus size={17} />Create examination</Link> : <Link className="button button-secondary" to="/exams">View examinations <ArrowRight size={16} /></Link>} /><WorkspaceVisual exams={exams} /><div className="stats-row"><Stat label="Visible assessments" value={exams.length} icon={CalendarDays} /><Stat label="Published windows" value={exams.filter((exam) => exam.status !== 'DRAFT').length} icon={CheckCircle2} tone="green" /><Stat label="Enrolled candidates" value={exams.reduce((sum, exam) => sum + exam.enrolled, 0)} icon={Users} tone="blue" /></div><Calendar exams={exams} role={user.role} /></>;
}

function Stat({ label, value, icon: Icon, tone = 'blue' }: { label: string; value: ReactNode; icon: typeof Users; tone?: string }) { return <div className="stat-card"><span className={`stat-icon ${tone}`}><Icon size={18} /></span><span className="stat-copy"><small>{label}</small><strong>{value}</strong></span></div>; }

function WorkspaceVisual({ exams }: { exams: Exam[] }) {
  const published = exams.filter((exam) => exam.status !== 'DRAFT').length;
  const enrolled = exams.reduce((sum, exam) => sum + exam.enrolled, 0);
  return <section className="workspace-visual card" aria-label="Assessment workspace summary"><div className="workspace-tiles"><div className="workspace-tile tile-blue"><CalendarDays size={20} /><strong>Schedule</strong><small>{exams.length ? `${exams.length} visible assessment${exams.length === 1 ? '' : 's'}` : 'No assessments yet'}</small></div><div className="workspace-tile tile-lilac"><FileQuestion size={20} /><strong>Question bank</strong><small>Build clear, focused questions</small></div><div className="workspace-tile tile-green"><Users size={20} /><strong>Participation</strong><small>{enrolled ? `${enrolled} enrolled candidate${enrolled === 1 ? '' : 's'}` : 'No candidates enrolled'}</small></div><div className="workspace-tile tile-amber"><BarChart3 size={20} /><strong>Insights</strong><small>{published ? `${published} published window${published === 1 ? '' : 's'}` : 'Results appear after publication'}</small></div></div></section>;
}


function Exams() {
  const [exams, setExams] = useState<Exam[] | null>(null);
  const [error, setError] = useState('');
  useEffect(() => { api.listExams().then(setExams).catch((reason) => setError(reason instanceof Error ? reason.message : 'Unable to load examinations.')); }, []);
  if (error) return <ErrorState error={error} />;
  if (!exams) return <LoadingState />;
  return <><PageHeader eyebrow="Candidate workspace" title="My examinations" /><div className="exam-list">{exams.length === 0 ? <div className="empty-state card"><BookOpen size={27} /><h2>No examinations assigned</h2><p className="muted">An administrator will add you to an assessment roster when it is ready.</p></div> : exams.map((exam) => <ExamRow exam={exam} key={exam.id} />)}</div></>;
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

type QuestionFormState = {
  type: QuestionInput['type'];
  category: string;
  difficulty: QuestionInput['difficulty'];
  prompt: string;
  options: Array<{ index: number; text: string }>;
  correctOptionIndices: number[];
  positiveMarks: string;
  negativeMarks: string;
};

function blankQuestionForm(): QuestionFormState {
  return {
    type: 'MCQ_SINGLE',
    category: '',
    difficulty: 'MEDIUM',
    prompt: '',
    options: [0, 1, 2, 3].map((index) => ({ index, text: '' })),
    correctOptionIndices: [],
    positiveMarks: '1',
    negativeMarks: '0',
  };
}

function questionToForm(question: Question): QuestionFormState {
  const correctOptionIndices = question.correctOptionIds?.map((id) => question.options.findIndex((option) => option.id === id)).filter((index) => index >= 0) ?? [];
  return {
    type: question.type,
    category: question.category,
    difficulty: question.difficulty,
    prompt: question.prompt,
    options: question.options.map((option, index) => ({ index, text: option.text })),
    correctOptionIndices,
    positiveMarks: String(question.positiveMarks ?? 1),
    negativeMarks: String(question.negativeMarks ?? 0),
  };
}

function QuestionBank() {
  const navigate = useNavigate();
  const [questions, setQuestions] = useState<Question[] | null>(null);
  const [form, setForm] = useState<QuestionFormState>(() => blankQuestionForm());
  const [editingId, setEditingId] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [saving, setSaving] = useState(false);

  const load = () => {
    setError('');
    setQuestions(null);
    void api.listAdminQuestions().then(setQuestions).catch((reason) => setError(reason instanceof Error ? reason.message : 'Unable to load the question bank.'));
  };

  useEffect(load, []);

  const updateForm = <K extends keyof QuestionFormState>(key: K, value: QuestionFormState[K]) => setForm((current) => ({ ...current, [key]: value }));
  const toggleCorrect = (index: number) => updateForm('correctOptionIndices', form.type === 'MCQ_SINGLE'
    ? [index]
    : form.correctOptionIndices.includes(index)
      ? form.correctOptionIndices.filter((value) => value !== index)
      : [...form.correctOptionIndices, index].sort((a, b) => a - b));
  const updateOption = (index: number, text: string) => updateForm('options', form.options.map((option) => option.index === index ? { ...option, text } : option));
  const addOption = () => { if (form.options.length < 10) updateForm('options', [...form.options, { index: form.options.length, text: '' }]); };
  const removeOption = (index: number) => {
    if (form.options.length <= 2) return;
    const options = form.options.filter((option) => option.index !== index).map((option, nextIndex) => ({ ...option, index: nextIndex }));
    const correctOptionIndices = form.correctOptionIndices.filter((value) => value !== index).map((value) => value > index ? value - 1 : value);
    setForm((current) => ({ ...current, options, correctOptionIndices }));
  };
  const startNew = () => { setEditingId(null); setForm(blankQuestionForm()); setError(''); setNotice(''); };
  const edit = (question: Question) => { setEditingId(question.id); setForm(questionToForm(question)); setError(''); setNotice(''); window.scrollTo({ top: 0, behavior: 'smooth' }); };
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setNotice('');
    if (form.options.some((option) => !option.text.trim())) { setError('Every option needs text before the question can be saved.'); return; }
    if (!form.correctOptionIndices.length || (form.type === 'MCQ_SINGLE' && form.correctOptionIndices.length !== 1)) { setError(form.type === 'MCQ_SINGLE' ? 'Select exactly one correct answer.' : 'Select at least one correct answer.'); return; }
    const input: QuestionInput = { ...form, options: form.options.map((option) => ({ ...option, text: option.text.trim() })), prompt: form.prompt.trim(), category: form.category.trim(), positiveMarks: Number(form.positiveMarks), negativeMarks: Number(form.negativeMarks) };
    setSaving(true);
    try {
      const saved = editingId ? await api.updateQuestion(editingId, input) : await api.createQuestion(input);
      setQuestions((current) => editingId ? (current ?? []).map((question) => question.id === saved.id ? saved : question) : [saved, ...(current ?? [])]);
      setNotice(editingId ? 'Question updated.' : 'Question added to your question bank.');
      setEditingId(null);
      setForm(blankQuestionForm());
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : 'Unable to save the question.');
    } finally { setSaving(false); }
  };

  if (error && !questions) return <ErrorState error={error} onRetry={load} />;
  if (!questions) return <LoadingState />;
  return <><PageHeader eyebrow="Teacher workspace" title="Question bank" description="Create the MCQs you can select when building an examination." action={<Button variant="secondary" onClick={() => navigate('/teacher/exams/create')}><FileQuestion size={16} />Build an examination</Button>} /><div className="question-bank-grid"><section className="card form-card question-editor"><div className="section-heading"><div><h2>{editingId ? 'Edit question' : 'Add a question'}</h2><p className="muted">Use single-choice or multi-select MCQs with an explicit answer key.</p></div>{editingId && <Button variant="ghost" type="button" onClick={startNew}>New question</Button>}</div><form className="stack" onSubmit={(event) => void save(event)}><div className="form-grid"><label>Question type<select value={form.type} onChange={(event) => { const type = event.target.value as QuestionInput['type']; updateForm('type', type); if (type === 'MCQ_SINGLE' && form.correctOptionIndices.length > 1) updateForm('correctOptionIndices', form.correctOptionIndices.slice(0, 1)); }}><option value="MCQ_SINGLE">Single choice</option><option value="MCQ_MULTI">Multiple select</option></select></label><label>Difficulty<select value={form.difficulty} onChange={(event) => updateForm('difficulty', event.target.value as QuestionInput['difficulty'])}><option value="EASY">Easy</option><option value="MEDIUM">Medium</option><option value="HARD">Hard</option></select></label><label className="span-2">Category<input value={form.category} onChange={(event) => updateForm('category', event.target.value)} placeholder="e.g. Java, Operating systems" required maxLength={100} /></label><label className="span-2">Question prompt<textarea rows={4} value={form.prompt} onChange={(event) => updateForm('prompt', event.target.value)} placeholder="Write the question shown to candidates" required /></label></div><div className="question-editor-heading"><div><h3>Answer options</h3><p className="muted">Select the correct answer{form.type === 'MCQ_MULTI' ? 's' : ''} using the control beside each option.</p></div><Button type="button" variant="secondary" onClick={addOption} disabled={form.options.length >= 10}><Plus size={15} />Add option</Button></div><div className="question-option-list">{form.options.map((option) => <div className="question-option-row" key={option.index}><label className="question-correct-control"><input type={form.type === 'MCQ_SINGLE' ? 'radio' : 'checkbox'} name="correct-option" checked={form.correctOptionIndices.includes(option.index)} onChange={() => toggleCorrect(option.index)} aria-label={`Mark option ${String.fromCharCode(65 + option.index)} as correct`} /><span>{String.fromCharCode(65 + option.index)}</span></label><input value={option.text} onChange={(event) => updateOption(option.index, event.target.value)} placeholder={`Option ${String.fromCharCode(65 + option.index)}`} required maxLength={2000} /><Button type="button" variant="ghost" onClick={() => removeOption(option.index)} disabled={form.options.length <= 2} aria-label={`Remove option ${String.fromCharCode(65 + option.index)}`}><Trash2 size={15} /></Button></div>)}</div><div className="form-grid"><label>Positive marks<input type="number" min="0.01" step="0.01" value={form.positiveMarks} onChange={(event) => updateForm('positiveMarks', event.target.value)} required /></label><label>Negative marks<input type="number" min="0" step="0.01" value={form.negativeMarks} onChange={(event) => updateForm('negativeMarks', event.target.value)} required /></label></div>{notice && <div className="alert success">{notice}</div>}{error && <div className="alert error">{error}</div>}<div className="builder-actions"><Button type="button" variant="secondary" onClick={startNew}>Clear</Button><Button type="submit" disabled={saving}>{saving ? 'Saving…' : editingId ? 'Save question' : 'Add question'} <Save size={15} /></Button></div></form></section><section className="card form-card"><div className="section-heading"><div><h2>Your questions</h2><p className="muted">Only questions you create are available to your examinations.</p></div><Badge tone="blue">{questions.length}</Badge></div>{questions.length === 0 ? <div className="empty-state question-bank-empty"><FileQuestion size={27} /><h3>No questions yet</h3><p className="muted">Create your first MCQ on the left to make it available in an examination.</p></div> : <div className="question-bank-list">{questions.map((question) => <article className="question-bank-item" key={question.id}><div className="question-bank-item-top"><div><strong>{question.prompt}</strong><small>{question.category} · {question.difficulty} · {question.type === 'MCQ_MULTI' ? 'Multiple select' : 'Single choice'} · {question.positiveMarks} marks</small></div><Button type="button" variant="secondary" onClick={() => edit(question)}>Edit</Button></div><div className="question-bank-options">{question.options.map((option) => <span className={question.correctOptionIds?.includes(option.id) ? 'correct' : ''} key={option.id}>{option.label}. {option.text}</span>)}</div></article>)}</div>}</section></div></>;
}

function CreateExam() {
  const navigate = useNavigate();
  const [questions, setQuestions] = useState<Question[] | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [filter, setFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState<'ALL' | Question['type']>('ALL');
  const [values, setValues] = useState({ title: '', subject: '', description: '', start: '', end: '', duration: '60', shuffleQuestions: true, shuffleOptions: true });
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => { api.listAdminQuestions().then(setQuestions).catch((reason) => setError(reason instanceof Error ? reason.message : 'Unable to load the question bank.')); }, []);

  const selectedQuestions = questions ? selected.map((id) => questions.find((question) => question.id === id)).filter((question): question is Question => Boolean(question)) : [];
  const filteredQuestions = questions?.filter((question) => {
    const search = filter.trim().toLowerCase();
    const matchesSearch = !search || question.prompt.toLowerCase().includes(search) || question.category.toLowerCase().includes(search);
    return matchesSearch && (typeFilter === 'ALL' || question.type === typeFilter);
  }) ?? [];
  const totalMarks = selectedQuestions.reduce((sum, question) => sum + (question.positiveMarks ?? 0), 0);
  const selectQuestion = (id: string) => setSelected((current) => current.includes(id) ? current.filter((value) => value !== id) : [...current, id]);
  const moveSelected = (id: string, direction: -1 | 1) => setSelected((current) => { const index = current.indexOf(id); const nextIndex = index + direction; if (index < 0 || nextIndex < 0 || nextIndex >= current.length) return current; const next = [...current]; [next[index], next[nextIndex]] = [next[nextIndex], next[index]]; return next; });
  const continueTo = (nextStep: 1 | 2 | 3) => {
    setError('');
    if (nextStep > 1 && (!values.title.trim() || !values.subject.trim() || !values.description.trim())) { setError('Complete the examination details before continuing.'); setStep(1); return; }
    setStep(nextStep);
  };
  const save = async () => {
    setSaving(true);
    setError('');
    try {
      const start = new Date(values.start);
      const end = new Date(values.end);
      if (!values.start || !values.end || Number.isNaN(start.valueOf()) || Number.isNaN(end.valueOf())) throw new Error('Choose a valid availability window.');
      const input: ExamInput = { title: values.title.trim(), subject: values.subject.trim(), description: values.description.trim(), windowStartTime: start.toISOString(), windowEndTime: end.toISOString(), durationMinutes: Number(values.duration), questionIds: selected, shuffleQuestions: values.shuffleQuestions, shuffleOptions: values.shuffleOptions };
      await api.createExam(input);
      navigate('/teacher/exams');
    } catch (reason) { setError(reason instanceof ApiError || reason instanceof Error ? reason.message : 'Unable to save the examination.'); }
    finally { setSaving(false); }
  };
  if (!questions) return error ? <ErrorState error={error} /> : <LoadingState />;
  const stepLabels = [{ number: 1, label: 'Details', hint: 'Name and instructions' }, { number: 2, label: 'Questions', hint: 'Build the question set' }, { number: 3, label: 'Schedule', hint: 'Timing and rules' }];
  return <><PageHeader eyebrow="Teacher workspace" title="Add examination" description="Create the activity first, then assemble its question set like a quiz editor." action={<Button variant="secondary" onClick={() => navigate('/teacher/exams')}>Cancel</Button>} /><div className="exam-builder-layout"><aside className="card exam-builder-rail"><div className="exam-builder-rail-title"><div><span className="eyebrow">New activity</span><h2>Draft examination</h2></div><Badge tone="blue">Draft</Badge></div><nav className="exam-builder-steps" aria-label="Examination setup steps">{stepLabels.map((item) => <button type="button" className={step === item.number ? 'exam-builder-step active' : 'exam-builder-step'} onClick={() => continueTo(item.number as 1 | 2 | 3)} key={item.number}><span>{item.number}</span><strong>{item.label}</strong><small>{item.hint}</small></button>)}</nav><div className="exam-builder-summary"><div><span>Questions</span><strong>{selected.length}</strong></div><div><span>Total marks</span><strong>{totalMarks}</strong></div><p>Save as a draft while you build. Publishing happens from the examination detail page after the question set is ready.</p></div></aside><main className="exam-builder-main">{step === 1 && <section className="card form-card"><div className="section-heading"><div><h2>Examination details</h2><p className="muted">This is the setup page candidates see before entering the assessment.</p></div><span className="builder-step-count">Step 1 of 3</span></div><div className="form-grid"><label>Examination title<input value={values.title} onChange={(event) => setValues({ ...values, title: event.target.value })} placeholder="e.g. Java fundamentals assessment" required /></label><label>Subject or category<input value={values.subject} onChange={(event) => setValues({ ...values, subject: event.target.value })} placeholder="e.g. Computer science" required /></label><label className="span-2">Instructions<textarea rows={6} value={values.description} onChange={(event) => setValues({ ...values, description: event.target.value })} placeholder="Explain the rules and expectations for candidates" required /></label></div></section>}{step === 2 && <section className="card form-card"><div className="section-heading"><div><h2>Build the question set</h2><p className="muted">Add new questions or select ready MCQs from your question bank. Reorder them before saving.</p></div><span className="builder-step-count">Step 2 of 3</span></div><div className="builder-choice-actions"><Link className="button button-secondary" to="/teacher/questions"><Plus size={16} />New question</Link><span className="muted">Or select from your existing question bank below.</span></div><div className="question-set-workspace"><div className="question-source"><div className="question-source-header"><div><h3>Question bank</h3><small>{filteredQuestions.length} available</small></div><select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value as 'ALL' | Question['type'])} aria-label="Filter question type"><option value="ALL">All types</option><option value="MCQ_SINGLE">Single choice</option><option value="MCQ_MULTI">Multiple select</option></select></div><input value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="Filter by prompt or category" aria-label="Filter question bank" />{filteredQuestions.length === 0 ? <div className="empty-inline"><FileQuestion size={17} /><span>No matching questions. <Link to="/teacher/questions">Create one in the question bank</Link>.</span></div> : <div className="question-source-list">{filteredQuestions.map((question) => <button type="button" className={selected.includes(question.id) ? 'question-source-item selected' : 'question-source-item'} onClick={() => selectQuestion(question.id)} key={question.id}><span className="question-source-mark">{selected.includes(question.id) ? <Check size={15} /> : <Plus size={15} />}</span><span><strong>{question.prompt}</strong><small>{question.category} · {question.type === 'MCQ_MULTI' ? 'Multiple select' : 'Single choice'} · {question.positiveMarks} marks</small></span></button>)}</div>}</div><div className="selected-question-panel"><div className="question-source-header"><div><h3>Questions in this examination</h3><small>{selected.length} selected</small></div></div>{selectedQuestions.length === 0 ? <div className="empty-inline"><span>Select a question from the bank to add it here.</span></div> : <div className="selected-question-list">{selectedQuestions.map((question, index) => <div className="selected-question-item" key={question.id}><span className="question-number">{index + 1}</span><div><strong>{question.prompt}</strong><small>{question.positiveMarks} marks · {question.type === 'MCQ_MULTI' ? 'Multiple select' : 'Single choice'}</small></div><div className="selected-question-actions"><button type="button" className="icon-button" onClick={() => moveSelected(question.id, -1)} disabled={index === 0} aria-label="Move question up"><ChevronDown size={15} className="rotate-180" /></button><button type="button" className="icon-button" onClick={() => moveSelected(question.id, 1)} disabled={index === selectedQuestions.length - 1} aria-label="Move question down"><ChevronDown size={15} /></button><button type="button" className="icon-button" onClick={() => selectQuestion(question.id)} aria-label="Remove question"><X size={15} /></button></div></div>)}</div>}</div></div></section>}{step === 3 && <section className="card form-card"><div className="section-heading"><div><h2>Schedule and rules</h2><p className="muted">The server validates the window, duration, and publication rules.</p></div><span className="builder-step-count">Step 3 of 3</span></div><div className="form-grid"><label>Availability start<input type="datetime-local" value={values.start} onChange={(event) => setValues({ ...values, start: event.target.value })} required /></label><label>Availability end<input type="datetime-local" value={values.end} onChange={(event) => setValues({ ...values, end: event.target.value })} required /></label><label>Duration (minutes)<input type="number" min="1" value={values.duration} onChange={(event) => setValues({ ...values, duration: event.target.value })} required /></label></div><div className="toggle-row"><label><input type="checkbox" checked={values.shuffleQuestions} onChange={(event) => setValues({ ...values, shuffleQuestions: event.target.checked })} /> Shuffle question order</label><label><input type="checkbox" checked={values.shuffleOptions} onChange={(event) => setValues({ ...values, shuffleOptions: event.target.checked })} /> Shuffle option order</label></div><div className="builder-review"><strong>Ready to save?</strong><span>{values.title || 'Untitled examination'} · {selected.length} questions · {totalMarks} marks · {values.duration || 0} minutes</span></div></section>}{error && <div className="alert error">{error}</div>}<div className="builder-actions"><Button type="button" variant="secondary" onClick={() => step === 1 ? navigate('/teacher/exams') : setStep((step - 1) as 1 | 2 | 3)}>{step === 1 ? 'Cancel' : 'Back'}</Button>{step < 3 ? <Button type="button" onClick={() => continueTo((step + 1) as 1 | 2 | 3)}>Continue <ArrowRight size={15} /></Button> : <Button type="button" disabled={saving || !values.title.trim() || !values.subject.trim() || !values.description.trim() || !values.start || !values.end || Number(values.duration) <= 0} onClick={() => void save()}>{saving ? 'Saving…' : 'Save draft'} <Save size={15} /></Button>}</div></main></div></>;
}

function toLocalDateTime(iso: string) {
  const date = new Date(iso);
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60_000).toISOString().slice(0, 16);
}

function TeacherControlPanel() {
  const { examId } = useParams();
  const [exam, setExam] = useState<Exam | null>(null);
  const [questions, setQuestions] = useState<Question[]>([]);
  const [candidates, setCandidates] = useState<AdminCandidate[]>([]);
  const [selectedQuestions, setSelectedQuestions] = useState<string[]>([]);
  const [selectedCandidates, setSelectedCandidates] = useState<string[]>([]);
  const [results, setResults] = useState<AdminResult[]>([]);
  const [analytics, setAnalytics] = useState<ExamAnalytics | null>(null);
  const [panel, setPanel] = useState<'overview' | 'settings' | 'roster' | 'results' | 'analytics'>('overview');
  const [candidateSearch, setCandidateSearch] = useState('');
  const [questionSearch, setQuestionSearch] = useState('');
  const [values, setValues] = useState({ title: '', subject: '', description: '', start: '', end: '', duration: '60', shuffleQuestions: true, shuffleOptions: true });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(async () => {
    if (!examId) return;
    setLoading(true);
    setError('');
    try {
      const [loadedExam, loadedQuestions, loadedCandidates, roster, loadedResults, loadedAnalytics] = await Promise.all([
        api.getAdminExam(examId), api.listAdminQuestions(), api.listCandidates(), api.getRoster(examId), api.getAdminResults(examId), api.getExamAnalytics(examId),
      ]);
      setExam(loadedExam);
      setQuestions(loadedQuestions);
      setCandidates(loadedCandidates);
      setSelectedQuestions(loadedExam.questions.map((question) => question.id));
      setSelectedCandidates(roster);
      setResults(loadedResults);
      setAnalytics(loadedAnalytics);
      setValues({ title: loadedExam.title, subject: loadedExam.subject, description: loadedExam.description, start: toLocalDateTime(loadedExam.start), end: toLocalDateTime(loadedExam.end), duration: String(loadedExam.durationMinutes), shuffleQuestions: loadedExam.shuffleQuestions, shuffleOptions: loadedExam.shuffleOptions });
    } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to load the examination controls.'); }
    finally { setLoading(false); }
  }, [examId]);

  useEffect(() => { void load(); }, [load]);

  const updateSettings = async () => {
    if (!exam || exam.status === 'PUBLISHED') return;
    setSaving(true); setError(''); setNotice('');
    try {
      const start = new Date(values.start);
      const end = new Date(values.end);
      if (Number.isNaN(start.valueOf()) || Number.isNaN(end.valueOf())) throw new Error('Choose a valid availability window.');
      const updated = await api.updateExam(exam.id, { title: values.title.trim(), subject: values.subject.trim(), description: values.description.trim(), windowStartTime: start.toISOString(), windowEndTime: end.toISOString(), durationMinutes: Number(values.duration), questionIds: selectedQuestions, shuffleQuestions: values.shuffleQuestions, shuffleOptions: values.shuffleOptions });
      setExam(updated); setNotice('Examination settings saved.');
    } catch (reason) { setError(reason instanceof ApiError || reason instanceof Error ? reason.message : 'Unable to save examination settings.'); }
    finally { setSaving(false); }
  };

  const updateRoster = async () => {
    if (!exam || exam.status === 'PUBLISHED') return;
    setSaving(true); setError(''); setNotice('');
    try { await api.replaceRoster(exam.id, selectedCandidates); await load(); setNotice('Candidate roster saved.'); }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to save the candidate roster.'); }
    finally { setSaving(false); }
  };

  const publishExam = async () => {
    if (!exam) return;
    setPublishing(true); setError(''); setNotice('');
    try { setExam(await api.publishExam(exam.id)); setNotice('Examination published and locked.'); }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to publish the examination.'); }
    finally { setPublishing(false); }
  };

  const publishResults = async () => {
    if (!exam) return;
    setPublishing(true); setError(''); setNotice('');
    try { const publication = await api.publishResults(exam.id); await load(); setNotice(`${publication.publishedCount} result${publication.publishedCount === 1 ? '' : 's'} published.`); }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to publish results.'); }
    finally { setPublishing(false); }
  };

  if (loading) return <LoadingState />;
  if (error && !exam) return <ErrorState error={error} onRetry={() => void load()} />;
  if (!exam || !analytics) return <ErrorState error="Examination data is unavailable." onRetry={() => void load()} />;
  const published = exam.status === 'PUBLISHED';
  const windowClosed = Date.now() >= Date.parse(exam.end);
  const filteredCandidates = candidates.filter((candidate) => `${candidate.fullName} ${candidate.email} ${candidate.rollNumber ?? ''}`.toLowerCase().includes(candidateSearch.toLowerCase()));
  const filteredQuestions = questions.filter((question) => !selectedQuestions.includes(question.id) && `${question.prompt} ${question.category}`.toLowerCase().includes(questionSearch.toLowerCase()));
  const selectedQuestionObjects = selectedQuestions.map((id) => questions.find((question) => question.id === id)).filter((question): question is Question => Boolean(question));

  return <><Link className="back-link" to="/teacher/exams"><ArrowLeft size={15} />Back to examinations</Link><PageHeader eyebrow="Examination control" title={exam.title} description={`${exam.subject} · ${exam.questions.length} questions · ${exam.durationMinutes} minutes`} action={<Badge tone={published ? 'green' : 'blue'}>{exam.status}</Badge>} />{notice && <div className="alert success">{notice}</div>}{error && <div className="alert error">{error}</div>}<div className="admin-control-tabs" role="tablist" aria-label="Examination control sections">{(['overview', 'settings', 'roster', 'results', 'analytics'] as const).map((item) => <button type="button" role="tab" aria-selected={panel === item} className={panel === item ? 'admin-control-tab active' : 'admin-control-tab'} onClick={() => setPanel(item)} key={item}>{item[0].toUpperCase() + item.slice(1)}</button>)}</div>{panel === 'overview' && <><div className="stats-row"><Stat label="Enrolled" value={exam.enrolled} icon={Users} /><Stat label="Questions" value={exam.questions.length} icon={FileQuestion} tone="blue" /><Stat label="Graded results" value={analytics.gradedResults} icon={BarChart3} tone="green" /><Stat label="Published results" value={analytics.publishedResults} icon={Send} tone="blue" /></div><section className="card publish-card"><div className="eyebrow">Publication control</div><h2>{published ? 'Exam is published' : 'Draft examination'}</h2><p className="muted">{published ? 'The schedule, question set, and roster are locked.' : 'Complete the settings and roster before publishing this assessment.'}</p><div className="builder-actions"><Button disabled={publishing || published} onClick={() => void publishExam()}>{publishing ? 'Publishing…' : published ? 'Published' : 'Publish examination'} <Send size={15} /></Button><Button variant="secondary" disabled={publishing || !published || !windowClosed} onClick={() => void publishResults()}>{publishing ? 'Publishing…' : 'Publish all results'} <Send size={15} /></Button></div>{published && !windowClosed && <p className="muted">Results can be published after the availability window closes.</p>}</section></>}{panel === 'settings' && <section className="card form-card"><div className="section-heading"><div><h2>Assessment settings</h2><p className="muted">Configure the exam candidates will receive. Published assessments are immutable.</p></div><Badge tone={published ? 'green' : 'blue'}>{published ? 'Locked' : 'Editable'}</Badge></div><div className="form-grid"><label>Examination title<input disabled={published} value={values.title} onChange={(event) => setValues({ ...values, title: event.target.value })} /></label><label>Subject or category<input disabled={published} value={values.subject} onChange={(event) => setValues({ ...values, subject: event.target.value })} /></label><label className="span-2">Instructions<textarea disabled={published} rows={5} value={values.description} onChange={(event) => setValues({ ...values, description: event.target.value })} /></label><label>Availability start<input disabled={published} type="datetime-local" value={values.start} onChange={(event) => setValues({ ...values, start: event.target.value })} /></label><label>Availability end<input disabled={published} type="datetime-local" value={values.end} onChange={(event) => setValues({ ...values, end: event.target.value })} /></label><label>Duration (minutes)<input disabled={published} type="number" min="1" value={values.duration} onChange={(event) => setValues({ ...values, duration: event.target.value })} /></label></div><div className="toggle-row"><label><input disabled={published} type="checkbox" checked={values.shuffleQuestions} onChange={(event) => setValues({ ...values, shuffleQuestions: event.target.checked })} /> Shuffle question order</label><label><input disabled={published} type="checkbox" checked={values.shuffleOptions} onChange={(event) => setValues({ ...values, shuffleOptions: event.target.checked })} /> Shuffle option order</label></div><div className="builder-actions"><Button disabled={saving || published} onClick={() => void updateSettings()}>{saving ? 'Saving…' : 'Save settings'} <Save size={15} /></Button></div></section>}{panel === 'settings' && <section className="card form-card"><div className="section-heading"><div><h2>Question set</h2><p className="muted">Select and order questions from your question bank.</p></div><span className="builder-step-count">{selectedQuestionObjects.length} selected</span></div><input disabled={published} value={questionSearch} onChange={(event) => setQuestionSearch(event.target.value)} placeholder="Search available questions" aria-label="Search available questions" />{!published && filteredQuestions.length > 0 && <div className="question-source-list compact">{filteredQuestions.map((question) => <button type="button" className="question-source-item" onClick={() => setSelectedQuestions((current) => [...current, question.id])} key={question.id}><span className="question-source-mark"><Plus size={15} /></span><span><strong>{question.prompt}</strong><small>{question.category} · {question.positiveMarks} marks</small></span></button>)}</div>}<div className="selected-question-list">{selectedQuestionObjects.map((question, index) => <div className="selected-question-item" key={question.id}><span className="question-number">{index + 1}</span><div><strong>{question.prompt}</strong><small>{question.positiveMarks} marks</small></div>{!published && <div className="selected-question-actions"><button type="button" className="icon-button" disabled={index === 0} onClick={() => setSelectedQuestions((current) => { const next = [...current]; [next[index - 1], next[index]] = [next[index], next[index - 1]]; return next; })} aria-label="Move question up"><ChevronDown size={15} className="rotate-180" /></button><button type="button" className="icon-button" disabled={index === selectedQuestionObjects.length - 1} onClick={() => setSelectedQuestions((current) => { const next = [...current]; [next[index], next[index + 1]] = [next[index + 1], next[index]]; return next; })} aria-label="Move question down"><ChevronDown size={15} /></button><button type="button" className="icon-button" onClick={() => setSelectedQuestions((current) => current.filter((id) => id !== question.id))} aria-label="Remove question"><X size={15} /></button></div>}</div>)}</div></section>}{panel === 'roster' && <section className="card form-card"><div className="section-heading"><div><h2>Candidate roster</h2><p className="muted">Only selected active candidates can access this assessment.</p></div><Badge tone="blue">{selectedCandidates.length} selected</Badge></div><input disabled={published} value={candidateSearch} onChange={(event) => setCandidateSearch(event.target.value)} placeholder="Search by name, email, or roll number" aria-label="Search candidates" /><div className="candidate-roster-list">{filteredCandidates.map((candidate) => <label className="candidate-roster-row" key={candidate.id}><input disabled={published} type="checkbox" checked={selectedCandidates.includes(candidate.id)} onChange={() => setSelectedCandidates((current) => current.includes(candidate.id) ? current.filter((id) => id !== candidate.id) : [...current, candidate.id])} /><span><strong>{candidate.fullName}</strong><small>{candidate.email}{candidate.rollNumber ? ` · ${candidate.rollNumber}` : ''}</small></span></label>)}</div><div className="builder-actions"><Button disabled={saving || published} onClick={() => void updateRoster()}>{saving ? 'Saving…' : 'Save roster'} <Save size={15} /></Button></div></section>}{panel === 'results' && <section className="card form-card"><div className="section-heading"><div><h2>Results</h2><p className="muted">Review all rostered candidates before publishing their results.</p></div><Button disabled={publishing || !published || !windowClosed} onClick={() => void publishResults()}>{publishing ? 'Publishing…' : 'Publish all results'} <Send size={15} /></Button></div><div className="table-scroll"><table className="admin-results-table"><thead><tr><th>Candidate</th><th>Attempt</th><th>Score</th><th>Correct</th><th>Result</th></tr></thead><tbody>{results.map((result) => <tr key={result.candidateId}><td><strong>{result.fullName}</strong><small>{result.email}{result.rollNumber ? ` · ${result.rollNumber}` : ''}</small></td><td><Badge tone={result.attemptStatus === 'NOT_STARTED' ? 'blue' : 'green'}>{result.attemptStatus.replace('_', ' ')}</Badge></td><td>{result.percentage == null ? '—' : `${result.percentage}% (${result.totalMarks}/${result.maximumMarks})`}</td><td>{result.totalMarks == null ? '—' : `${result.correct} / ${result.correct + result.incorrect + result.unattempted}`}</td><td><Badge tone={result.published ? 'green' : result.eligible ? 'amber' : 'blue'}>{result.published ? 'Published' : result.eligible ? 'Ready' : 'Pending'}</Badge></td></tr>)}</tbody></table>{results.length === 0 && <div className="empty-inline">No rostered candidates yet.</div>}</div></section>}{panel === 'analytics' && <section className="card form-card"><div className="section-heading"><div><h2>Performance analytics</h2><p className="muted">Computed synchronously from submitted and expired attempts.</p></div><Badge tone="blue">{analytics.gradedResults} graded</Badge></div><div className="analytics-metric-grid"><Stat label="Average" value={analytics.averagePercentage == null ? '—' : `${analytics.averagePercentage}%`} icon={BarChart3} /><Stat label="Median" value={analytics.medianPercentage == null ? '—' : `${analytics.medianPercentage}%`} icon={BarChart3} tone="blue" /><Stat label="Started" value={analytics.started} icon={Users} tone="green" /><Stat label="Not started" value={analytics.notStarted} icon={Clock3} tone="red" /></div><div className="analytics-grid"><div><h3>Score distribution</h3><div className="score-distribution">{analytics.scoreDistribution.map((bucket) => <div className="score-bar-row" key={bucket.label}><span>{bucket.label}</span><div className="score-bar"><i style={{ width: `${analytics.gradedResults ? Math.max(4, bucket.count / analytics.gradedResults * 100) : 0}%` }} /></div><strong>{bucket.count}</strong></div>)}</div></div><div><h3>Attempt status</h3><div className="analytics-status-list"><span>Submitted <strong>{analytics.submitted}</strong></span><span>Expired <strong>{analytics.expired}</strong></span><span>Published results <strong>{analytics.publishedResults}</strong></span></div></div></div><h3>Per-question correctness</h3><div className="table-scroll"><table className="admin-results-table"><thead><tr><th>Question</th><th>Attempted</th><th>Correct</th><th>Incorrect</th><th>Accuracy</th></tr></thead><tbody>{analytics.questionCorrectness.map((question) => <tr key={question.questionId}><td><strong>{question.prompt}</strong></td><td>{question.attempted}</td><td>{question.correct}</td><td>{question.incorrect}</td><td>{question.accuracyPercentage}%</td></tr>)}</tbody></table></div></section>}{panel === 'overview' && <section className="card form-card"><h2>Assessment lifecycle</h2><p className="muted">Configure the draft, assign the roster, publish the examination, then review and publish eligible results after the window closes.</p><div className="lifecycle-list"><span className={published ? 'done' : 'current'}>1. Examination published</span><span className={selectedCandidates.length ? 'done' : 'current'}>2. Candidate roster assigned</span><span className={analytics.gradedResults ? 'done' : 'current'}>3. Attempts graded</span><span className={analytics.publishedResults ? 'done' : 'current'}>4. Results published</span></div></section>}</>;
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
function Profile() {
  const current = getCurrentUser();
  const [user, setUser] = useState(current);
  const [fullName, setFullName] = useState(current?.fullName ?? '');
  const [rollNumber, setRollNumber] = useState(current?.rollNumber ?? '');
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    let mounted = true;
    void api.getProfile().then((profile) => {
      if (!mounted) return;
      setUser(profile);
      setFullName(profile.fullName);
      setRollNumber(profile.rollNumber ?? '');
    }).catch((reason) => {
      if (mounted) setError(reason instanceof ApiError ? reason.message : 'Unable to load profile details.');
    });
    return () => { mounted = false; };
  }, []);

  const update = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true); setMessage(''); setError('');
    try { const updated = await api.updateProfile({ fullName, rollNumber }); setUser(updated); setMessage('Profile details saved.'); }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to save profile details.'); }
    finally { setSaving(false); }
  };
  const upload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    setUploading(true); setMessage(''); setError('');
    try { setUser(await api.uploadAvatar(file)); setMessage('Profile picture updated.'); }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to upload profile picture.'); }
    finally { setUploading(false); }
  };
  const remove = async () => {
    setUploading(true); setMessage(''); setError('');
    try { setUser(await api.removeAvatar()); setMessage('Profile picture removed.'); }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to remove profile picture.'); }
    finally { setUploading(false); }
  };
  if (!user) return <Navigate to="/auth/login" replace />;
  return <><PageHeader eyebrow="Account" title="Profile" description="Manage the details shown on your Assessly account." /><div className="profile-grid"><section className="card profile-card"><div className="profile-avatar-large">{user.avatarUrl ? <img src={user.avatarUrl} alt={`${user.fullName}'s profile`} /> : user.fullName.slice(0, 1)}</div><h2>{user.fullName}</h2><p className="muted">{user.email}</p><div className="profile-avatar-actions"><label className="button button-secondary"><Upload size={15} />{uploading ? 'Uploading…' : 'Upload picture'}<input className="visually-hidden" type="file" accept="image/jpeg,image/png,image/gif" onChange={upload} disabled={uploading} /></label>{user.avatarUrl && <Button variant="ghost" onClick={() => void remove()} disabled={uploading} icon={<Trash2 size={15} />}>Remove</Button>}</div><small className="muted">JPEG, PNG, or GIF up to 5 MB.</small></section><section className="card form-card" id="settings"><div className="section-heading"><div><div className="eyebrow">Account settings</div><h2>Personal details</h2></div><UserRound size={20} /></div><form className="stack" onSubmit={update}><label>Full name<input value={fullName} onChange={(event) => setFullName(event.target.value)} required maxLength={255} /></label><label>Email<input value={user.email} readOnly /></label>{user.role === 'ROLE_CANDIDATE' && <label>Roll number<input value={rollNumber} onChange={(event) => setRollNumber(event.target.value)} maxLength={64} /></label>}{message && <div className="alert success">{message}</div>}{error && <div className="alert error">{error}</div>}<Button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save changes'} <Check size={16} /></Button></form></section></div></>;
}
function Unauthorized() { return <div className="empty-state card"><ShieldCheck size={30} /><h1>Access restricted</h1><p className="muted">This workspace is not available for your role.</p><Link to="/dashboard" className="button button-primary">Return to dashboard</Link></div>; }

function App() {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let mounted = true;
    void api.restoreSession().catch(() => undefined).finally(() => {
      if (mounted) setReady(true);
    });
    return () => { mounted = false; };
  }, []);

  if (!ready) return <LoadingState />;
  return <Routes><Route element={<PublicShell />}><Route path="/" element={<Navigate to="/auth/login" replace />} /><Route path="/auth/login" element={<Login />} /><Route path="/auth/signup" element={<Signup />} /><Route path="/admin/login" element={<AdminLogin />} /></Route><Route element={<Protected />}><Route path="/dashboard" element={<Dashboard />} /><Route path="/profile" element={<Profile />} /><Route path="/exams" element={<Exams />} /><Route path="/exam/:examId/lobby" element={<Lobby />} /><Route path="/exam/:examId/live" element={<LiveExamRoute />} /><Route path="/results" element={<Results />} /><Route path="/results/:examId" element={<Results />} /></Route><Route element={<Protected role="ROLE_ADMIN" />}><Route path="/teacher/questions" element={<QuestionBank />} /><Route path="/teacher/exams" element={<TeacherExams />} /><Route path="/teacher/exams/create" element={<CreateExam />} /><Route path="/teacher/exams/:examId" element={<TeacherControlPanel />} /></Route><Route path="/unauthorized" element={<Unauthorized />} /><Route path="*" element={<Navigate to="/dashboard" replace />} /></Routes>;
}

createRoot(document.getElementById('root')!).render(<StrictMode><BrowserRouter><App /></BrowserRouter></StrictMode>);
