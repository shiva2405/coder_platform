import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Clock, Database, Play, Send } from 'lucide-react';
import AppNav from '../components/AppNav';
import CodeEditor from '../components/CodeEditor';
import UserMenu from '../components/UserMenu';
import JudgeResults from '../components/JudgeResults';
import LanguageSelector from '../components/LanguageSelector';
import { getProblem, getSubmission, listSubmissions, runSamples, submitSolution } from '../services/api';
import { loadLanguages } from '../services/defaultLanguages';
import { RateLimitedError, rateLimitFromAxios } from '../services/rateLimit';
import { starterCode } from '../services/problemTemplates';
import {
  Difficulty,
  EditorTheme,
  JudgeResult,
  Language,
  ProblemDetail,
  QueueStatus,
  SubmissionSummary,
} from '../types';

const difficultyClass: Record<Difficulty, string> = {
  EASY: 'bg-green-900/40 text-green-300',
  MEDIUM: 'bg-yellow-900/40 text-yellow-300',
  HARD: 'bg-red-900/40 text-red-300',
};

function formatMemory(bytes: number): string {
  return `${Math.round(bytes / (1024 * 1024))}MB`;
}

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

export default function ProblemSolvePage() {
  const { slug } = useParams<{ slug: string }>();
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [languages, setLanguages] = useState<Language[]>([]);
  const [selectedLanguage, setSelectedLanguage] = useState<Language | null>(null);
  const [code, setCode] = useState('');
  const [theme] = useState<EditorTheme>('vs-dark');
  const [result, setResult] = useState<JudgeResult | null>(null);
  const [resultMode, setResultMode] = useState<'samples' | 'submit'>('samples');
  const [busy, setBusy] = useState<'samples' | 'submit' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submissions, setSubmissions] = useState<SubmissionSummary[]>([]);
  const [queue, setQueue] = useState<QueueStatus | null>(null);
  const [cooldownUntil, setCooldownUntil] = useState(0);
  const [now, setNow] = useState(() => Date.now());

  const loadSubmissions = useCallback(async (problemSlug: string) => {
    try {
      setSubmissions(await listSubmissions(problemSlug));
    } catch (err) {
      console.error(err);
    }
  }, []);

  useEffect(() => {
    if (!slug) {
      return;
    }
    const load = async () => {
      try {
        const [problemData, languageLoad] = await Promise.all([getProblem(slug), loadLanguages()]);
        const langs = languageLoad.languages;
        setProblem(problemData);
        setLanguages(langs);
        const python = langs.find((language) => language.id === 'python') || langs[0] || null;
        setSelectedLanguage(python);
        setCode(starterCode(python?.id || 'python'));
        setError(null);
        await loadSubmissions(slug);
      } catch (err) {
        console.error(err);
        setProblem(null);
        setError('Problem not found.');
      }
    };
    load();
  }, [slug, loadSubmissions]);

  const handleLanguageSelect = (language: Language) => {
    setSelectedLanguage(language);
    setCode(starterCode(language.id));
    setResult(null);
  };

  const applyRateLimit = (err: unknown) => {
    const limited = err instanceof RateLimitedError ? err : rateLimitFromAxios(err);
    if (!limited) {
      return null;
    }
    setCooldownUntil(Date.now() + limited.retryAfterSeconds * 1000);
    setError(limited.message);
    return limited;
  };

  useEffect(() => {
    if (cooldownUntil <= Date.now()) {
      return;
    }
    const timer = window.setInterval(() => setNow(Date.now()), 250);
    return () => window.clearInterval(timer);
  }, [cooldownUntil]);

  const cooldownSeconds = Math.max(0, Math.ceil((cooldownUntil - now) / 1000));

  const handleRunSamples = useCallback(async () => {
    if (!slug || !selectedLanguage || busy || cooldownUntil > Date.now()) {
      return;
    }
    setBusy('samples');
    setResultMode('samples');
    setError(null);
    setQueue(null);
    try {
      setResult(await runSamples(slug, selectedLanguage.id, code, undefined, setQueue));
    } catch (err: any) {
      if (!applyRateLimit(err)) {
        setError(err.response?.data?.error || err.message || 'Failed to run samples.');
      }
    } finally {
      setQueue(null);
      setBusy(null);
    }
  }, [slug, selectedLanguage, code, busy, cooldownUntil]);

  const handleSubmit = useCallback(async () => {
    if (!slug || !selectedLanguage || busy || cooldownUntil > Date.now()) {
      return;
    }
    setBusy('submit');
    setResultMode('submit');
    setError(null);
    setQueue(null);
    try {
      const judged = await submitSolution(slug, selectedLanguage.id, code, undefined, setQueue);
      setResult(judged);
      await loadSubmissions(slug);
    } catch (err: any) {
      if (!applyRateLimit(err)) {
        setError(err.response?.data?.error || err.message || 'Failed to submit.');
      }
    } finally {
      setQueue(null);
      setBusy(null);
    }
  }, [slug, selectedLanguage, code, busy, loadSubmissions, cooldownUntil]);

  useEffect(() => {
    const onRun = () => {
      handleRunSamples();
    };
    window.addEventListener('run-code', onRun);
    return () => window.removeEventListener('run-code', onRun);
  }, [handleRunSamples]);

  const openSubmission = async (id: number) => {
    try {
      const detail = await getSubmission(id);
      setResult(detail);
      setResultMode('submit');
      setError(null);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to load submission.');
    }
  };

  return (
    <div className="h-full flex flex-col bg-editor-bg text-gray-200">
      <header className="flex items-center justify-between px-4 py-3 bg-editor-sidebar border-b border-editor-border">
        <AppNav current="problems" />
        <div className="flex items-center gap-2">
          <UserMenu />
          <LanguageSelector
            languages={languages}
            selectedLanguage={selectedLanguage}
            onSelect={handleLanguageSelect}
          />
          <button
            onClick={handleRunSamples}
            disabled={!selectedLanguage || !!busy || !problem || cooldownSeconds > 0}
            className={`flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium ${
              !selectedLanguage || busy || !problem || cooldownSeconds > 0
                ? 'bg-gray-700 text-gray-400 cursor-not-allowed'
                : 'bg-blue-600 hover:bg-blue-700 text-white'
            }`}
          >
            <Play className="w-4 h-4" />
            {cooldownSeconds > 0
              ? `Wait ${cooldownSeconds}s`
              : busy === 'samples'
                ? (queue ? `Queued #${queue.position}` : 'Running...')
                : 'Run Samples'}
          </button>
          <button
            onClick={handleSubmit}
            disabled={!selectedLanguage || !!busy || !problem || cooldownSeconds > 0}
            className={`flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium ${
              !selectedLanguage || busy || !problem || cooldownSeconds > 0
                ? 'bg-gray-700 text-gray-400 cursor-not-allowed'
                : 'bg-green-600 hover:bg-green-700 text-white'
            }`}
          >
            <Send className="w-4 h-4" />
            {cooldownSeconds > 0
              ? `Wait ${cooldownSeconds}s`
              : busy === 'submit'
                ? (queue ? `Queued #${queue.position}` : 'Submitting...')
                : 'Submit'}
          </button>
        </div>
      </header>

      {error && (
        <div className="px-4 py-2 bg-yellow-700 text-white text-sm text-center">{error}</div>
      )}
      {queue && !error && (
        <div className="px-4 py-2 bg-amber-800 text-amber-50 text-sm text-center">
          Queued at position {queue.position}
          {queue.estimatedWaitMs > 0 ? ` · about ${Math.max(1, Math.round(queue.estimatedWaitMs / 1000))}s` : ''}
        </div>
      )}

      <div className="flex-1 min-h-0 flex flex-col lg:flex-row">
        <aside className="lg:w-[42%] lg:max-w-[640px] border-b lg:border-b-0 lg:border-r border-editor-border overflow-y-auto">
          <div className="p-5">
            {problem ? (
              <>
                <div className="flex items-start justify-between gap-3 mb-3">
                  <div>
                    <Link to="/problems" className="text-xs text-gray-500 hover:text-gray-300">
                      ← All problems
                    </Link>
                    <h1 className="text-xl font-semibold mt-1">{problem.title}</h1>
                  </div>
                  <span className={`px-2 py-0.5 rounded text-xs font-medium ${difficultyClass[problem.difficulty]}`}>
                    {problem.difficulty}
                  </span>
                </div>
                <div className="flex flex-wrap gap-3 text-xs text-gray-400 mb-4">
                  <span className="inline-flex items-center gap-1">
                    <Clock className="w-3.5 h-3.5" />
                    {problem.timeLimitMs}ms
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <Database className="w-3.5 h-3.5" />
                    {formatMemory(problem.memoryLimitBytes)}
                  </span>
                  <span>{problem.hiddenTestCount} hidden test{problem.hiddenTestCount === 1 ? '' : 's'}</span>
                </div>
                <div className="flex flex-wrap gap-1 mb-4">
                  {problem.tags.map((tag) => (
                    <span key={tag} className="px-2 py-0.5 rounded bg-editor-border text-xs text-gray-300">
                      {tag}
                    </span>
                  ))}
                </div>
                <pre className="whitespace-pre-wrap text-sm text-gray-200 leading-relaxed font-sans">
                  {problem.description}
                </pre>

                {problem.samples.length > 0 && (
                  <div className="mt-6 space-y-4">
                    <h2 className="text-sm uppercase tracking-wider text-gray-500">Sample tests</h2>
                    {problem.samples.map((sample) => (
                      <div key={sample.index} className="rounded-md border border-editor-border overflow-hidden">
                        <div className="px-3 py-2 bg-editor-sidebar text-xs text-gray-400">
                          Sample {sample.index} • {sample.points} pts
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-0 text-xs font-mono">
                          <div className="p-3 border-b sm:border-b-0 sm:border-r border-editor-border">
                            <div className="text-gray-500 mb-1">Input</div>
                            <pre className="whitespace-pre-wrap">{sample.input}</pre>
                          </div>
                          <div className="p-3">
                            <div className="text-gray-500 mb-1">Output</div>
                            <pre className="whitespace-pre-wrap">{sample.expectedOutput}</pre>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                {submissions.length > 0 && (
                  <div className="mt-8">
                    <h2 className="text-sm uppercase tracking-wider text-gray-500 mb-2">Recent submissions</h2>
                    <div className="space-y-1">
                      {submissions.slice(0, 8).map((submission) => (
                        <button
                          key={submission.id}
                          onClick={() => openSubmission(submission.id)}
                          className="w-full text-left px-3 py-2 rounded border border-editor-border hover:bg-white/5 text-xs flex items-center justify-between gap-2"
                        >
                          <span className={submission.verdict === 'ACCEPTED' ? 'text-green-400' : 'text-red-300'}>
                            {submission.verdict.replace(/_/g, ' ')}
                          </span>
                          <span className="text-gray-500">
                            {submission.language} • {submission.passedCount}/{submission.totalCount} • {formatTime(submission.createdAt)}
                          </span>
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </>
            ) : (
              <div className="text-gray-500">{error || 'Loading problem...'}</div>
            )}
          </div>
        </aside>

        <section className="flex-1 min-h-0 flex flex-col">
          <div className="flex-1 min-h-[240px] border-b border-editor-border">
            <CodeEditor
              code={code}
              onChange={(value) => setCode(value || '')}
              language={selectedLanguage?.id || 'python'}
              theme={theme}
            />
          </div>
          <div className="h-[42%] min-h-[200px] overflow-y-auto">
            <JudgeResults result={result} isLoading={!!busy} mode={resultMode} />
          </div>
        </section>
      </div>
    </div>
  );
}
