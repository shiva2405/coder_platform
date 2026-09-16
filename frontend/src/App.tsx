import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Eye } from 'lucide-react';
import { useAuth } from './auth/AuthProvider';
import CodeEditor from './components/CodeEditor';
import ConfirmDialog from './components/ConfirmDialog';
import EditorTabs from './components/EditorTabs';
import FileExplorer from './components/FileExplorer';
import HistorySidebar from './components/HistorySidebar';
import LoginModal from './components/LoginModal';
import NameDialog from './components/NameDialog';
import OutputPanel from './components/OutputPanel';
import RunDiffModal from './components/RunDiffModal';
import Toolbar from './components/Toolbar';
import { useRunHistory } from './hooks/useRunHistory';
import { Language, ExecutionResponse, EditorTheme, QueueStatus, Snippet, RunHistoryEntry, SnippetVisibility, ProjectFile } from './types';
import { executeCode, forkSnippet, getSnippet, saveSnippet, updateSnippet } from './services/api';
import { defaultSampleCodes, loadLanguages } from './services/defaultLanguages';
import { draftsDiffer, loadDraft, saveDraft } from './services/draftStorage';
import { LiveUnavailableError, LiveRunSession, startLiveRun } from './services/liveExecution';
import {
  defaultFileName,
  defaultProject,
  entrypointContent,
  joinPath,
  normalizeProjectPath,
  parentDir,
  projectsEqual,
  replaceFile,
  snapshotFromSnippet,
  validateProject,
} from './services/projectFiles';
import { RateLimitedError, rateLimitFromAxios } from './services/rateLimit';
import { historyToResult } from './services/runHistoryStorage';

const HISTORY_OPEN_KEY = 'coder-platform:history-open';

function readHistoryOpen(): boolean {
  try {
    const raw = localStorage.getItem(HISTORY_OPEN_KEY);
    return raw === null ? true : raw === 'true';
  } catch {
    return true;
  }
}

interface OriginSnippet {
  slug: string;
  language: string;
  code: string;
  files: ProjectFile[];
  entrypoint: string;
  stdin: string;
  viewCount: number;
  title: string | null;
  visibility: SnippetVisibility;
  ownedByMe: boolean;
}

type NameDialogState =
  | { mode: 'file'; folder?: string }
  | { mode: 'folder'; folder?: string }
  | { mode: 'rename'; path: string };

function sampleFor(language: Language): string {
  return language.sampleCode || defaultSampleCodes[language.id] || '';
}

async function copyText(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.setAttribute('readonly', '');
  textarea.style.position = 'absolute';
  textarea.style.left = '-9999px';
  document.body.appendChild(textarea);
  textarea.select();
  document.execCommand('copy');
  document.body.removeChild(textarea);
}

function App() {
  const { slug: routeSlug } = useParams<{ slug?: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [languages, setLanguages] = useState<Language[]>([]);
  const [selectedLanguage, setSelectedLanguage] = useState<Language | null>(null);
  const [files, setFiles] = useState<ProjectFile[]>([]);
  const [entrypoint, setEntrypoint] = useState('');
  const [openTabs, setOpenTabs] = useState<string[]>([]);
  const [activePath, setActivePath] = useState('');
  const [extraFolders, setExtraFolders] = useState<string[]>([]);
  const [stdin, setStdin] = useState<string>('');
  const [nameDialog, setNameDialog] = useState<NameDialogState | null>(null);
  const [nameError, setNameError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<string | null>(null);
  const [result, setResult] = useState<ExecutionResponse | null>(null);
  const [isRunning, setIsRunning] = useState(false);
  const [isSharing, setIsSharing] = useState(false);
  const [shareCopied, setShareCopied] = useState(false);
  const [theme, setTheme] = useState<EditorTheme>('vs-dark');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [originSnippet, setOriginSnippet] = useState<OriginSnippet | null>(null);
  const [ready, setReady] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(readHistoryOpen);
  const [viewedRunId, setViewedRunId] = useState<string | null>(null);
  const [compareIds, setCompareIds] = useState<string[]>([]);
  const [languageFilter, setLanguageFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [diffOpen, setDiffOpen] = useState(false);
  const [pendingRestore, setPendingRestore] = useState<RunHistoryEntry | null>(null);
  const [pendingClear, setPendingClear] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
  const [liveMode, setLiveMode] = useState(false);
  const [liveOutput, setLiveOutput] = useState('');
  const [liveError, setLiveError] = useState('');
  const [inputClosed, setInputClosed] = useState(false);
  const [queue, setQueue] = useState<QueueStatus | null>(null);
  const [cooldownUntil, setCooldownUntil] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  const loadedSlugRef = useRef<string | null>(null);
  const copyResetRef = useRef<number | undefined>(undefined);
  const baselineRef = useRef<{ language: string; files: ProjectFile[]; entrypoint: string; stdin: string } | null>(null);
  const liveSessionRef = useRef<LiveRunSession | null>(null);
  const restAbortRef = useRef<AbortController | null>(null);
  const interactiveStdinRef = useRef('');
  const runFinishedRef = useRef(false);
  const { runs, error: historyError, recordRun, clearHistory } = useRunHistory();

  const setBaseline = (language: string, nextFiles: ProjectFile[], nextEntrypoint: string, nextStdin: string) => {
    baselineRef.current = { language, files: nextFiles, entrypoint: nextEntrypoint, stdin: nextStdin };
  };

  const applyProject = (nextFiles: ProjectFile[], nextEntrypoint: string, nextTabs?: string[], nextActive?: string) => {
    setFiles(nextFiles);
    setEntrypoint(nextEntrypoint);
    const tabs = nextTabs && nextTabs.length > 0
      ? nextTabs.filter((path) => nextFiles.some((file) => file.path === path))
      : [nextEntrypoint];
    const resolvedTabs = tabs.length > 0 ? tabs : [nextEntrypoint];
    setOpenTabs(resolvedTabs);
    const active = nextActive && nextFiles.some((file) => file.path === nextActive)
      ? nextActive
      : resolvedTabs[0];
    setActivePath(active);
  };

  const openFile = (path: string) => {
    setOpenTabs((tabs) => (tabs.includes(path) ? tabs : [...tabs, path]));
    setActivePath(path);
  };

  const closeTab = (path: string) => {
    setOpenTabs((tabs) => {
      const next = tabs.filter((tab) => tab !== path);
      if (path === activePath) {
        setActivePath(next[next.length - 1] || files[0]?.path || '');
      }
      return next.length > 0 ? next : tabs;
    });
  };

  const activeCode = files.find((file) => file.path === activePath)?.content ?? '';

  const applyLanguage = useCallback((langs: Language[], languageId: string): Language | null => {
    return langs.find((language) => language.id === languageId) || langs[0] || null;
  }, []);

  const applyDefaultEditor = useCallback((langs: Language[]) => {
    const defaultLang = langs.find((language) => language.id === 'python') || langs[0];
    if (!defaultLang) {
      return;
    }
    setSelectedLanguage(defaultLang);
    const project = defaultProject(defaultLang.id, sampleFor(defaultLang));
    applyProject(project.files, project.entrypoint);
    setExtraFolders([]);
    setStdin('');
    setOriginSnippet(null);
    setBaseline(defaultLang.id, project.files, project.entrypoint, '');
  }, []);

  const applyDraft = useCallback((langs: Language[], draft: ReturnType<typeof loadDraft>) => {
    if (!draft) {
      return;
    }
    const language = applyLanguage(langs, draft.languageId);
    if (language) {
      setSelectedLanguage(language);
    }
    const project = snapshotFromSnippet(draft.languageId, draft.code, draft.files, draft.entrypoint);
    applyProject(project.files, project.entrypoint, draft.openTabs, draft.activePath);
    setStdin(draft.stdin);
    setBaseline(draft.languageId, project.files, project.entrypoint, draft.stdin);
  }, [applyLanguage]);

  const applySnippet = useCallback((langs: Language[], snippet: Snippet) => {
    const language = applyLanguage(langs, snippet.language);
    if (language) {
      setSelectedLanguage(language);
    }
    const project = snapshotFromSnippet(snippet.language, snippet.code, snippet.files, snippet.entrypoint);
    applyProject(project.files, project.entrypoint);
    setStdin(snippet.stdin || '');
    setOriginSnippet({
      slug: snippet.slug,
      language: snippet.language,
      code: project.files.find((file) => file.path === project.entrypoint)?.content ?? snippet.code,
      files: project.files,
      entrypoint: project.entrypoint,
      stdin: snippet.stdin || '',
      viewCount: snippet.viewCount,
      title: snippet.title,
      visibility: snippet.visibility || 'PUBLIC',
      ownedByMe: Boolean(snippet.ownedByMe),
    });
    loadedSlugRef.current = snippet.slug;
    setBaseline(snippet.language, project.files, project.entrypoint, snippet.stdin || '');
  }, [applyLanguage]);

  useEffect(() => {
    const fetchLanguages = async () => {
      const { languages: langs, offline } = await loadLanguages();
      setLanguages(langs);
      if (offline) {
        setError('Failed to connect to server. Using offline mode.');
      }
    };
    fetchLanguages();
  }, []);

  useEffect(() => {
    if (languages.length === 0) {
      return;
    }

    const initialize = async () => {
      if (routeSlug) {
        if (loadedSlugRef.current === routeSlug) {
          setReady(true);
          return;
        }
        try {
          const snippet = await getSnippet(routeSlug);
          const draft = loadDraft(routeSlug);
          applySnippet(languages, snippet);
          if (draft && draftsDiffer(draft, {
            language: snippet.language,
            code: snippet.code,
            stdin: snippet.stdin || '',
            files: snippet.files,
            entrypoint: snippet.entrypoint,
          })) {
            applyDraft(languages, draft);
          }
          setError(null);
        } catch (err) {
          console.error('Failed to load snippet:', err);
          loadedSlugRef.current = routeSlug;
          setOriginSnippet(null);
          applyDefaultEditor(languages);
          setError('Snippet not found. You can start a new snippet and share it.');
        } finally {
          setReady(true);
        }
        return;
      }

      loadedSlugRef.current = null;
      const draft = loadDraft(null);
      if (draft) {
        applyDraft(languages, draft);
        setOriginSnippet(null);
      } else {
        applyDefaultEditor(languages);
      }
      setReady(true);
    };

    initialize();
  }, [languages, routeSlug, applyDefaultEditor, applyDraft, applySnippet]);

  useEffect(() => {
    if (!ready || !selectedLanguage) {
      return;
    }
    const timer = window.setTimeout(() => {
      saveDraft(routeSlug ?? null, {
        languageId: selectedLanguage.id,
        code: entrypointContent(files, entrypoint),
        files,
        entrypoint,
        openTabs,
        activePath,
        stdin,
      });
    }, 300);
    return () => window.clearTimeout(timer);
  }, [ready, selectedLanguage, files, entrypoint, openTabs, activePath, stdin, routeSlug]);

  useEffect(() => {
    try {
      localStorage.setItem(HISTORY_OPEN_KEY, String(historyOpen));
    } catch {
      // Ignore quota / private-mode failures
    }
  }, [historyOpen]);

  useEffect(() => {
    setCompareIds((ids) => {
      const next = ids.filter((id) => runs.some((run) => run.id === id));
      if (next.length === ids.length && next.every((id, index) => id === ids[index])) {
        return ids;
      }
      return next;
    });
    if (viewedRunId && !runs.some((run) => run.id === viewedRunId)) {
      setViewedRunId(null);
    }
  }, [runs, viewedRunId]);

  const applyRateLimit = useCallback((error: unknown) => {
    const limited = error instanceof RateLimitedError ? error : rateLimitFromAxios(error);
    if (!limited) {
      return null;
    }
    setCooldownUntil(Date.now() + limited.retryAfterSeconds * 1000);
    setError(limited.message);
    return limited;
  }, []);

  useEffect(() => {
    if (cooldownUntil <= Date.now()) {
      return;
    }
    const timer = window.setInterval(() => setNow(Date.now()), 250);
    return () => window.clearInterval(timer);
  }, [cooldownUntil]);

  const finishRun = useCallback(async (
    response: ExecutionResponse,
    runLanguage: string,
    runCode: string,
    runStdin: string,
    runFiles?: ProjectFile[],
    runEntrypoint?: string,
  ) => {
    if (runFinishedRef.current) {
      return;
    }
    runFinishedRef.current = true;
    liveSessionRef.current = null;
    restAbortRef.current = null;
    setQueue(null);
    setResult(response);
    setLiveOutput(response.output || '');
    setLiveError(response.error || '');
    setIsRunning(false);
    setLiveMode(false);
    setInputClosed(true);
    await recordRun({
      language: runLanguage,
      code: runCode,
      files: runFiles,
      entrypoint: runEntrypoint,
      stdin: runStdin,
      status: response.status,
      executionTime: response.executionTime,
      output: response.output,
      error: response.error,
    });
  }, [recordRun]);

  const runBuffered = useCallback(async (
    runLanguage: string,
    runCode: string,
    runStdin: string,
    announceFallback: boolean,
    runFiles: ProjectFile[],
    runEntrypoint: string,
  ) => {
    setLiveMode(false);
    if (announceFallback) {
      setNotice('Live execution unavailable. Using buffered run.');
      if (copyResetRef.current) {
        window.clearTimeout(copyResetRef.current);
      }
      copyResetRef.current = window.setTimeout(() => setNotice(null), 2500);
    }
    const controller = new AbortController();
    restAbortRef.current = controller;
    try {
      const response = await executeCode({
        language: runLanguage,
        code: runCode,
        files: runFiles,
        entrypoint: runEntrypoint,
        stdin: runStdin,
      }, controller.signal, setQueue);
      await finishRun(response, runLanguage, runCode, runStdin, runFiles, runEntrypoint);
    } catch (err: any) {
      if (err?.code === 'ERR_CANCELED' || err?.name === 'CanceledError' || err?.name === 'AbortError') {
        await finishRun({
          output: '',
          error: '',
          executionTime: 0,
          status: 'STOPPED',
        }, runLanguage, runCode, runStdin, runFiles, runEntrypoint);
        return;
      }
      const limited = applyRateLimit(err);
      console.error('Execution error:', err);
      await finishRun({
        output: '',
        error: limited?.message || err.response?.data?.error || err.message || 'Failed to execute code. Please try again.',
        executionTime: 0,
        status: 'ERROR',
      }, runLanguage, runCode, runStdin, runFiles, runEntrypoint);
    }
  }, [applyRateLimit, finishRun]);

  const handleRun = useCallback(async () => {
    if (!selectedLanguage || isRunning) return;

    if (cooldownUntil > Date.now()) {
      return;
    }

    runFinishedRef.current = false;
    interactiveStdinRef.current = '';
    setIsRunning(true);
    setResult(null);
    setLiveOutput('');
    setLiveError('');
    setLiveMode(false);
    setInputClosed(false);
    setViewedRunId(null);
    setError(null);
    setQueue(null);

    const projectError = validateProject(files, entrypoint);
    if (projectError) {
      setError(projectError);
      setIsRunning(false);
      return;
    }

    const runLanguage = selectedLanguage.id;
    const runFiles = files;
    const runEntrypoint = entrypoint;
    const runCode = entrypointContent(runFiles, runEntrypoint);
    const runStdin = stdin;

    try {
      const session = await startLiveRun({
        language: runLanguage,
        code: runCode,
        files: runFiles,
        entrypoint: runEntrypoint,
        stdin: runStdin,
      }, {
        onQueued: setQueue,
        onStarted: () => setQueue(null),
        onStdout: (chunk) => setLiveOutput((current) => current + chunk),
        onStderr: (chunk) => setLiveError((current) => current + chunk),
        onDone: (response) => {
          void finishRun(response, runLanguage, runCode, runStdin + interactiveStdinRef.current, runFiles, runEntrypoint);
        },
        onError: (message) => {
          void finishRun({
            output: '',
            error: message,
            executionTime: 0,
            status: 'ERROR',
          }, runLanguage, runCode, runStdin + interactiveStdinRef.current, runFiles, runEntrypoint);
        },
      });
      if (runFinishedRef.current) {
        session.close();
        return;
      }
      liveSessionRef.current = session;
      setLiveMode(true);
    } catch (err) {
      if (err instanceof LiveUnavailableError) {
        await runBuffered(runLanguage, runCode, runStdin, true, runFiles, runEntrypoint);
        return;
      }
      const limited = applyRateLimit(err);
      if (limited) {
        await finishRun({
          output: '',
          error: limited.message,
          executionTime: 0,
          status: 'ERROR',
        }, runLanguage, runCode, runStdin, runFiles, runEntrypoint);
        return;
      }
      console.error('Live execution error:', err);
      await finishRun({
        output: '',
        error: err instanceof Error ? err.message : 'Failed to execute code. Please try again.',
        executionTime: 0,
        status: 'ERROR',
      }, runLanguage, runCode, runStdin, runFiles, runEntrypoint);
    }
  }, [selectedLanguage, files, entrypoint, stdin, isRunning, cooldownUntil, finishRun, runBuffered, applyRateLimit]);

  const handleStop = useCallback(() => {
    if (liveSessionRef.current) {
      liveSessionRef.current.stop();
      return;
    }
    if (restAbortRef.current) {
      restAbortRef.current.abort();
    }
  }, []);

  useEffect(() => {
    const handleRunCode = () => {
      if (isRunning) {
        handleStop();
      } else if (selectedLanguage) {
        void handleRun();
      }
    };
    window.addEventListener('run-code', handleRunCode);
    return () => window.removeEventListener('run-code', handleRunCode);
  }, [isRunning, selectedLanguage, handleRun, handleStop]);

  useEffect(() => {
    const abandon = () => {
      liveSessionRef.current?.close();
    };
    window.addEventListener('pagehide', abandon);
    return () => {
      window.removeEventListener('pagehide', abandon);
      abandon();
    };
  }, []);

  const handleLanguageSelect = (language: Language) => {
    const previous = selectedLanguage;
    setSelectedLanguage(language);
    const onlyDefaultFile = files.length === 1 && previous
      && files[0].path === defaultFileName(previous.id)
      && files[0].content === sampleFor(previous);
    if (onlyDefaultFile || files.length === 0) {
      const project = defaultProject(language.id, sampleFor(language));
      applyProject(project.files, project.entrypoint);
      setExtraFolders([]);
    }
    setResult(null);
    setError(null);
  };

  const handleCodeChange = (value: string | undefined) => {
    if (!activePath) {
      return;
    }
    setFiles((current) => replaceFile(current, activePath, value || ''));
  };

  const handleReset = () => {
    if (originSnippet) {
      const language = applyLanguage(languages, originSnippet.language);
      if (language) {
        setSelectedLanguage(language);
      }
      applyProject(originSnippet.files, originSnippet.entrypoint);
      setStdin(originSnippet.stdin);
      setResult(null);
      setError(null);
      setBaseline(originSnippet.language, originSnippet.files, originSnippet.entrypoint, originSnippet.stdin);
      return;
    }
    if (selectedLanguage) {
      const project = defaultProject(selectedLanguage.id, sampleFor(selectedLanguage));
      applyProject(project.files, project.entrypoint);
      setExtraFolders([]);
      setStdin('');
      setResult(null);
      setError(null);
      setBaseline(selectedLanguage.id, project.files, project.entrypoint, '');
    }
  };

  const isEditorDirty = () => {
    const baseline = baselineRef.current;
    if (!baseline || !selectedLanguage) {
      return false;
    }
    return !projectsEqual(
      { languageId: selectedLanguage.id, files, entrypoint, stdin },
      { languageId: baseline.language, files: baseline.files, entrypoint: baseline.entrypoint, stdin: baseline.stdin },
    );
  };

  const applyRestore = (run: RunHistoryEntry) => {
    const language = applyLanguage(languages, run.language);
    if (language) {
      setSelectedLanguage(language);
    }
    const project = snapshotFromSnippet(run.language, run.code, run.files, run.entrypoint);
    applyProject(project.files, project.entrypoint);
    setStdin(run.stdin);
    setResult(historyToResult(run));
    setViewedRunId(null);
    setError(null);
    setBaseline(run.language, project.files, project.entrypoint, run.stdin);
    setNotice('Restored project files and stdin from the selected run.');
    if (copyResetRef.current) {
      window.clearTimeout(copyResetRef.current);
    }
    copyResetRef.current = window.setTimeout(() => setNotice(null), 2500);
  };

  const requestRestore = (run: RunHistoryEntry) => {
    if (isEditorDirty()) {
      setPendingRestore(run);
      return;
    }
    applyRestore(run);
  };

  const handleToggleCompare = (id: string) => {
    setCompareIds((prev) => {
      if (prev.includes(id)) {
        return prev.filter((value) => value !== id);
      }
      if (prev.length < 2) {
        return [...prev, id];
      }
      return [prev[1], id];
    });
  };

  const compareLeft = compareIds[0] ? runs.find((run) => run.id === compareIds[0]) : undefined;
  const compareRight = compareIds[1] ? runs.find((run) => run.id === compareIds[1]) : undefined;
  const viewedRun = viewedRunId ? runs.find((run) => run.id === viewedRunId) ?? null : null;
  const displayResult = viewedRun ? historyToResult(viewedRun) : result;

  const handleThemeToggle = () => {
    setTheme((prev) => (prev === 'vs-dark' ? 'light' : 'vs-dark'));
  };

  const isDirty = Boolean(
    originSnippet &&
      selectedLanguage &&
      !projectsEqual(
        { languageId: selectedLanguage.id, files, entrypoint, stdin },
        {
          languageId: originSnippet.language,
          files: originSnippet.files,
          entrypoint: originSnippet.entrypoint,
          stdin: originSnippet.stdin,
        },
      )
  );

  const shareUrlFor = (slug: string) => `${window.location.origin}/s/${slug}`;

  const markCopied = (message: string) => {
    setShareCopied(true);
    setNotice(message);
    if (copyResetRef.current) {
      window.clearTimeout(copyResetRef.current);
    }
    copyResetRef.current = window.setTimeout(() => {
      setShareCopied(false);
      setNotice(null);
    }, 2500);
  };

  const handleShare = async () => {
    if (!selectedLanguage || isSharing || !entrypointContent(files, entrypoint).trim()) {
      return;
    }
    const projectError = validateProject(files, entrypoint);
    if (projectError) {
      setError(projectError);
      return;
    }

    if (originSnippet && !isDirty) {
      try {
        await copyText(shareUrlFor(originSnippet.slug));
        markCopied('Link copied to clipboard.');
      } catch (err) {
        console.error('Copy failed:', err);
        setError('Saved, but copying the link failed. Copy it from the address bar.');
      }
      return;
    }

    if (!user) {
      setLoginOpen(true);
      return;
    }

    setIsSharing(true);
    setError(null);
    try {
      const payload = {
        language: selectedLanguage.id,
        code: entrypointContent(files, entrypoint),
        files,
        entrypoint,
        stdin,
      };
      let saved: Snippet;
      if (originSnippet?.ownedByMe) {
        saved = await updateSnippet(originSnippet.slug, payload);
      } else if (originSnippet) {
        saved = await forkSnippet(originSnippet.slug, payload);
      } else {
        saved = await saveSnippet(payload);
      }

      applySnippet(languages, saved);
      navigate(`/s/${saved.slug}`, { replace: true });
      await copyText(shareUrlFor(saved.slug));
      markCopied(
        originSnippet?.ownedByMe
          ? 'Saved. Link copied.'
          : originSnippet
            ? 'Forked and copied a new share link.'
            : 'Snippet saved. Link copied.',
      );
    } catch (err: any) {
      console.error('Share failed:', err);
      const status = err.response?.status;
      const serverError = err.response?.data?.error;
      if (status === 401) {
        setLoginOpen(true);
      } else if (applyRateLimit(err)) {
        // countdown is shown on the run button; banner uses the server message
      } else {
        setError(serverError || err.message || 'Failed to save snippet. Please try again.');
      }
    } finally {
      setIsSharing(false);
    }
  };

  const shareLabel = originSnippet && isDirty
    ? (originSnippet.ownedByMe ? 'Save' : 'Fork & Share')
    : 'Share';

  return (
    <div className={`h-screen flex flex-col ${theme === 'light' ? 'bg-white' : 'bg-editor-bg'}`}>
      {error && (
        <div className="px-4 py-2 bg-yellow-600 text-white text-sm text-center">
          {error}
        </div>
      )}
      {notice && !error && (
        <div className="px-4 py-2 bg-green-700 text-white text-sm text-center">
          {notice}
        </div>
      )}

      <Toolbar
        languages={languages}
        selectedLanguage={selectedLanguage}
        onLanguageSelect={handleLanguageSelect}
        onRun={handleRun}
        onStop={handleStop}
        onReset={handleReset}
        onShare={handleShare}
        isRunning={isRunning}
        isSharing={isSharing}
        shareCopied={shareCopied}
        canShare={Boolean(selectedLanguage && entrypointContent(files, entrypoint).trim())}
        shareLabel={shareLabel}
        theme={theme}
        onThemeToggle={handleThemeToggle}
        historyOpen={historyOpen}
        onToggleHistory={() => setHistoryOpen((open) => !open)}
        cooldownSeconds={Math.max(0, Math.ceil((cooldownUntil - now) / 1000))}
      />

      <div className="flex-1 flex overflow-hidden relative">
        {historyOpen && (
          <button
            type="button"
            className="absolute inset-y-0 left-72 right-0 z-20 bg-black/40 md:hidden"
            aria-label="Close history"
            onClick={() => setHistoryOpen(false)}
          />
        )}
        <HistorySidebar
          open={historyOpen}
          runs={runs}
          languages={languages}
          error={historyError}
          viewedRunId={viewedRunId}
          compareIds={compareIds}
          languageFilter={languageFilter}
          statusFilter={statusFilter}
          onLanguageFilter={setLanguageFilter}
          onStatusFilter={setStatusFilter}
          onSelectRun={(id) => setViewedRunId(id)}
          onToggleCompare={handleToggleCompare}
          onRestore={requestRestore}
          onCompare={() => {
            if (compareLeft && compareRight) {
              setDiffOpen(true);
            }
          }}
          onClear={() => setPendingClear(true)}
          onClose={() => setHistoryOpen(false)}
        />
        <FileExplorer
          files={files}
          extraFolders={extraFolders}
          activePath={activePath}
          entrypoint={entrypoint}
          onOpen={openFile}
          onNewFile={(folder) => {
            setNameError(null);
            setNameDialog({ mode: 'file', folder });
          }}
          onNewFolder={(folder) => {
            setNameError(null);
            setNameDialog({ mode: 'folder', folder });
          }}
          onRename={(path) => {
            setNameError(null);
            setNameDialog({ mode: 'rename', path });
          }}
          onDelete={(path) => setPendingDelete(path)}
          onSetEntrypoint={setEntrypoint}
        />
        <div className="flex-1 flex flex-col border-r border-editor-border min-w-0">
          <div className="px-4 py-2 bg-editor-sidebar border-b border-editor-border text-sm text-gray-400 flex items-center justify-between gap-3">
            {selectedLanguage ? (
              <span>
                {selectedLanguage.name} • {entrypoint || selectedLanguage.extension}
                {originSnippet?.title ? ` • ${originSnippet.title}` : ''}
                {isDirty ? (originSnippet?.ownedByMe ? ' • unsaved' : ' • unsaved fork') : ''}
              </span>
            ) : (
              <span>Select a language to start coding</span>
            )}
            {originSnippet && (
              <span className="flex items-center gap-3 text-xs">
                <span className="font-mono text-gray-500">/s/{originSnippet.slug}</span>
                <span className="uppercase tracking-wide text-gray-500">{originSnippet.visibility}</span>
                <span className="flex items-center gap-1">
                  <Eye className="w-3.5 h-3.5" />
                  {originSnippet.viewCount}
                </span>
              </span>
            )}
          </div>
          <EditorTabs
            tabs={openTabs}
            activePath={activePath}
            entrypoint={entrypoint}
            onSelect={setActivePath}
            onClose={closeTab}
          />
          <div className="flex-1 min-h-0">
            <CodeEditor
              path={activePath}
              code={activeCode}
              onChange={handleCodeChange}
              language={selectedLanguage?.id || 'plaintext'}
              theme={theme}
              knownPaths={files.map((file) => file.path)}
            />
          </div>
        </div>

        <div className="w-1/3 min-w-[300px] max-w-[600px]">
          <OutputPanel
            result={displayResult}
            isLoading={isRunning}
            live={liveMode}
            liveOutput={liveOutput}
            liveError={liveError}
            queue={queue}
            stdin={stdin}
            onStdinChange={setStdin}
            interactive={isRunning && liveMode}
            inputClosed={inputClosed}
            onSendInput={(line) => {
              interactiveStdinRef.current += line;
              liveSessionRef.current?.sendStdin(line);
            }}
            onCloseInput={() => {
              setInputClosed(true);
              liveSessionRef.current?.closeStdin();
            }}
            viewedRun={viewedRun}
            onRestoreRun={viewedRun ? () => requestRestore(viewedRun) : undefined}
            onShowLatest={() => setViewedRunId(null)}
          />
        </div>
      </div>

      {diffOpen && compareLeft && compareRight && (
        <RunDiffModal
          left={compareLeft}
          right={compareRight}
          theme={theme}
          onClose={() => setDiffOpen(false)}
        />
      )}

      <ConfirmDialog
        open={pendingRestore !== null}
        title="Restore this run?"
        message="The editor has unsaved changes. Restore will replace the current project files and stdin with the exact values from this run."
        confirmLabel="Restore"
        onConfirm={() => {
          if (pendingRestore) {
            applyRestore(pendingRestore);
          }
          setPendingRestore(null);
        }}
        onCancel={() => setPendingRestore(null)}
      />

      <LoginModal
        open={loginOpen}
        nextPath={window.location.pathname}
        onClose={() => setLoginOpen(false)}
      />

      <NameDialog
        key={nameDialog ? `${nameDialog.mode}:${'path' in nameDialog ? nameDialog.path : nameDialog.folder || ''}` : 'closed'}
        open={nameDialog !== null}
        title={
          nameDialog?.mode === 'rename'
            ? 'Rename file'
            : nameDialog?.mode === 'folder'
              ? 'New folder'
              : 'New file'
        }
        message={
          nameDialog?.mode === 'folder'
            ? 'Folders are created when you add a file inside them. Use paths like src or lib/util.'
            : 'Paths can include folders, for example src/Util.java'
        }
        label={nameDialog?.mode === 'folder' ? 'Folder path' : 'File path'}
        confirmLabel={nameDialog?.mode === 'rename' ? 'Rename' : 'Create'}
        initialValue={
          nameDialog?.mode === 'rename'
            ? nameDialog.path
            : nameDialog?.mode === 'folder'
              ? joinPath(nameDialog.folder || '', '')
              : joinPath(
                  nameDialog?.folder || '',
                  selectedLanguage ? defaultFileName(selectedLanguage.id) : 'main.txt',
                )
        }
        error={nameError}
        onCancel={() => {
          setNameDialog(null);
          setNameError(null);
        }}
        onSubmit={(value) => {
          try {
            if (nameDialog?.mode === 'folder') {
              const folder = normalizeProjectPath(`${value.replace(/\/+$/, '')}/placeholder.txt`).replace(/\/placeholder\.txt$/, '');
              if (!folder) {
                setNameError('Folder path is required');
                return;
              }
              setExtraFolders((current) => current.includes(folder) ? current : [...current, folder]);
              setNameDialog({ mode: 'file', folder });
              setNameError(null);
              return;
            }
            const path = normalizeProjectPath(value);
            if (nameDialog?.mode === 'rename') {
              if (files.some((file) => file.path === path && file.path !== nameDialog.path)) {
                setNameError('A file already exists at that path');
                return;
              }
              setFiles((current) => current.map((file) => (
                file.path === nameDialog.path ? { ...file, path } : file
              )));
              setOpenTabs((tabs) => tabs.map((tab) => (tab === nameDialog.path ? path : tab)));
              if (activePath === nameDialog.path) {
                setActivePath(path);
              }
              if (entrypoint === nameDialog.path) {
                setEntrypoint(path);
              }
            } else {
              if (files.some((file) => file.path === path)) {
                setNameError('A file already exists at that path');
                return;
              }
              if (files.length >= 32) {
                setNameError('Projects are limited to 32 files');
                return;
              }
              setFiles((current) => [...current, { path, content: '' }]);
              setExtraFolders((current) => current.filter((folder) => folder !== parentDir(path) && folder !== path));
              openFile(path);
            }
            setNameDialog(null);
            setNameError(null);
          } catch (error) {
            setNameError(error instanceof Error ? error.message : 'Invalid path');
          }
        }}
      />

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete this file?"
        message={pendingDelete ? `Remove ${pendingDelete} from the project.` : ''}
        confirmLabel="Delete"
        danger
        onConfirm={() => {
          if (!pendingDelete) {
            return;
          }
          if (files.length <= 1) {
            setError('A project needs at least one file.');
            setPendingDelete(null);
            return;
          }
          const remaining = files.filter((file) => file.path !== pendingDelete);
          setFiles(remaining);
          setOpenTabs((tabs) => tabs.filter((tab) => tab !== pendingDelete));
          if (activePath === pendingDelete) {
            setActivePath(remaining[0]?.path || '');
          }
          if (entrypoint === pendingDelete) {
            setEntrypoint(remaining[0]?.path || '');
          }
          setPendingDelete(null);
        }}
        onCancel={() => setPendingDelete(null)}
      />

      <ConfirmDialog
        open={pendingClear}
        title="Clear run history?"
        message="This removes the latest 50 stored runs from this browser. This cannot be undone."
        confirmLabel="Clear history"
        danger
        onConfirm={async () => {
          await clearHistory();
          setPendingClear(false);
          setViewedRunId(null);
          setCompareIds([]);
          setDiffOpen(false);
        }}
        onCancel={() => setPendingClear(false)}
      />

      <div className="px-4 py-2 bg-editor-sidebar border-t border-editor-border text-xs text-gray-500 flex justify-between">
        <span>
          Time Limit: 30s • Memory Limit: 128MB
        </span>
        <span>
          Press Ctrl+Enter to run or stop
        </span>
      </div>
    </div>
  );
}

export default App;
