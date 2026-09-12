import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Eye } from 'lucide-react';
import CodeEditor from './components/CodeEditor';
import ConfirmDialog from './components/ConfirmDialog';
import HistorySidebar from './components/HistorySidebar';
import OutputPanel from './components/OutputPanel';
import RunDiffModal from './components/RunDiffModal';
import Toolbar from './components/Toolbar';
import { useRunHistory } from './hooks/useRunHistory';
import { Language, ExecutionResponse, EditorTheme, Snippet, RunHistoryEntry } from './types';
import { executeCode, forkSnippet, getLanguages, getSnippet, saveSnippet } from './services/api';
import { draftsDiffer, loadDraft, saveDraft } from './services/draftStorage';
import { LiveUnavailableError, LiveRunSession, startLiveRun } from './services/liveExecution';
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

const MAX_CODE_BYTES = 256 * 1024;

// Default sample codes for fallback
const defaultSampleCodes: Record<string, string> = {
  java: `public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}`,
  python: `print("Hello, World!")`,
  javascript: `console.log("Hello, World!");`,
  typescript: `const greeting: string = "Hello, World!";
console.log(greeting);`,
  c: `#include <stdio.h>

int main() {
    printf("Hello, World!\\n");
    return 0;
}`,
  cpp: `#include <iostream>

int main() {
    std::cout << "Hello, World!" << std::endl;
    return 0;
}`,
  go: `package main

import "fmt"

func main() {
    fmt.Println("Hello, World!")
}`,
  rust: `fn main() {
    println!("Hello, World!");
}`,
  ruby: `puts "Hello, World!"`,
  php: `<?php
echo "Hello, World!\\n";
?>`,
  kotlin: `fun main() {
    println("Hello, World!")
}`,
  swift: `print("Hello, World!")`,
  perl: `print "Hello, World!\\n";`,
  bash: `#!/bin/bash
echo "Hello, World!"`,
};

interface OriginSnippet {
  slug: string;
  language: string;
  code: string;
  stdin: string;
  viewCount: number;
  title: string | null;
}

function utf8ByteLength(value: string): number {
  return new TextEncoder().encode(value).length;
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
  const [languages, setLanguages] = useState<Language[]>([]);
  const [selectedLanguage, setSelectedLanguage] = useState<Language | null>(null);
  const [code, setCode] = useState<string>('');
  const [stdin, setStdin] = useState<string>('');
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
  const [liveMode, setLiveMode] = useState(false);
  const [liveOutput, setLiveOutput] = useState('');
  const [liveError, setLiveError] = useState('');
  const [inputClosed, setInputClosed] = useState(false);
  const loadedSlugRef = useRef<string | null>(null);
  const copyResetRef = useRef<number | undefined>(undefined);
  const baselineRef = useRef<{ language: string; code: string; stdin: string } | null>(null);
  const liveSessionRef = useRef<LiveRunSession | null>(null);
  const restAbortRef = useRef<AbortController | null>(null);
  const interactiveStdinRef = useRef('');
  const runFinishedRef = useRef(false);
  const { runs, error: historyError, recordRun, clearHistory } = useRunHistory();

  const setBaseline = (language: string, nextCode: string, nextStdin: string) => {
    baselineRef.current = { language, code: nextCode, stdin: nextStdin };
  };

  const applyLanguage = useCallback((langs: Language[], languageId: string): Language | null => {
    return langs.find((language) => language.id === languageId) || langs[0] || null;
  }, []);

  const applyDefaultEditor = useCallback((langs: Language[]) => {
    const defaultLang = langs.find((language) => language.id === 'python') || langs[0];
    if (!defaultLang) {
      return;
    }
    setSelectedLanguage(defaultLang);
    const nextCode = defaultLang.sampleCode || defaultSampleCodes[defaultLang.id] || '';
    setCode(nextCode);
    setStdin('');
    setOriginSnippet(null);
    setBaseline(defaultLang.id, nextCode, '');
  }, []);

  const applyDraft = useCallback((langs: Language[], languageId: string, nextCode: string, nextStdin: string) => {
    const language = applyLanguage(langs, languageId);
    if (language) {
      setSelectedLanguage(language);
    }
    setCode(nextCode);
    setStdin(nextStdin);
    setBaseline(languageId, nextCode, nextStdin);
  }, [applyLanguage]);

  const applySnippet = useCallback((langs: Language[], snippet: Snippet) => {
    const language = applyLanguage(langs, snippet.language);
    if (language) {
      setSelectedLanguage(language);
    }
    setCode(snippet.code);
    setStdin(snippet.stdin || '');
    setOriginSnippet({
      slug: snippet.slug,
      language: snippet.language,
      code: snippet.code,
      stdin: snippet.stdin || '',
      viewCount: snippet.viewCount,
      title: snippet.title,
    });
    loadedSlugRef.current = snippet.slug;
    setBaseline(snippet.language, snippet.code, snippet.stdin || '');
  }, [applyLanguage]);

  useEffect(() => {
    const fetchLanguages = async () => {
      try {
        const langs = await getLanguages();
        setLanguages(langs);
      } catch (err) {
        console.error('Failed to fetch languages:', err);
        setError('Failed to connect to server. Using offline mode.');
        const defaultLangs: Language[] = Object.entries(defaultSampleCodes).map(([id, sampleCode]) => ({
          id,
          name: id.charAt(0).toUpperCase() + id.slice(1),
          extension: `.${id}`,
          sampleCode,
        }));
        setLanguages(defaultLangs);
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
          })) {
            applyDraft(languages, draft.languageId, draft.code, draft.stdin);
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
        applyDraft(languages, draft.languageId, draft.code, draft.stdin);
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
        code,
        stdin,
      });
    }, 300);
    return () => window.clearTimeout(timer);
  }, [ready, selectedLanguage, code, stdin, routeSlug]);

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

  const finishRun = useCallback(async (
    response: ExecutionResponse,
    runLanguage: string,
    runCode: string,
    runStdin: string,
  ) => {
    if (runFinishedRef.current) {
      return;
    }
    runFinishedRef.current = true;
    liveSessionRef.current = null;
    restAbortRef.current = null;
    setResult(response);
    setLiveOutput(response.output || '');
    setLiveError(response.error || '');
    setIsRunning(false);
    setLiveMode(false);
    setInputClosed(true);
    await recordRun({
      language: runLanguage,
      code: runCode,
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
        stdin: runStdin,
      }, controller.signal);
      await finishRun(response, runLanguage, runCode, runStdin);
    } catch (err: any) {
      if (err?.code === 'ERR_CANCELED' || err?.name === 'CanceledError' || err?.name === 'AbortError') {
        await finishRun({
          output: '',
          error: '',
          executionTime: 0,
          status: 'STOPPED',
        }, runLanguage, runCode, runStdin);
        return;
      }
      console.error('Execution error:', err);
      await finishRun({
        output: '',
        error: err.response?.data?.error || err.message || 'Failed to execute code. Please try again.',
        executionTime: 0,
        status: 'ERROR',
      }, runLanguage, runCode, runStdin);
    }
  }, [finishRun]);

  const handleRun = useCallback(async () => {
    if (!selectedLanguage || isRunning) return;

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

    const runLanguage = selectedLanguage.id;
    const runCode = code;
    const runStdin = stdin;

    try {
      const session = await startLiveRun({
        language: runLanguage,
        code: runCode,
        stdin: runStdin,
      }, {
        onStdout: (chunk) => setLiveOutput((current) => current + chunk),
        onStderr: (chunk) => setLiveError((current) => current + chunk),
        onDone: (response) => {
          void finishRun(response, runLanguage, runCode, runStdin + interactiveStdinRef.current);
        },
        onError: (message) => {
          void finishRun({
            output: '',
            error: message,
            executionTime: 0,
            status: 'ERROR',
          }, runLanguage, runCode, runStdin + interactiveStdinRef.current);
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
        await runBuffered(runLanguage, runCode, runStdin, true);
        return;
      }
      console.error('Live execution error:', err);
      await finishRun({
        output: '',
        error: err instanceof Error ? err.message : 'Failed to execute code. Please try again.',
        executionTime: 0,
        status: 'ERROR',
      }, runLanguage, runCode, runStdin);
    }
  }, [selectedLanguage, code, stdin, isRunning, finishRun, runBuffered]);

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
    setSelectedLanguage(language);
    setCode(language.sampleCode || defaultSampleCodes[language.id] || '');
    setResult(null);
    setError(null);
  };

  const handleCodeChange = (value: string | undefined) => {
    setCode(value || '');
  };

  const handleReset = () => {
    if (originSnippet) {
      const language = applyLanguage(languages, originSnippet.language);
      if (language) {
        setSelectedLanguage(language);
      }
      setCode(originSnippet.code);
      setStdin(originSnippet.stdin);
      setResult(null);
      setError(null);
      setBaseline(originSnippet.language, originSnippet.code, originSnippet.stdin);
      return;
    }
    if (selectedLanguage) {
      const nextCode = selectedLanguage.sampleCode || defaultSampleCodes[selectedLanguage.id] || '';
      setCode(nextCode);
      setStdin('');
      setResult(null);
      setError(null);
      setBaseline(selectedLanguage.id, nextCode, '');
    }
  };

  const isEditorDirty = () => {
    const baseline = baselineRef.current;
    if (!baseline || !selectedLanguage) {
      return false;
    }
    return (
      selectedLanguage.id !== baseline.language ||
      code !== baseline.code ||
      stdin !== baseline.stdin
    );
  };

  const applyRestore = (run: RunHistoryEntry) => {
    const language = applyLanguage(languages, run.language);
    if (language) {
      setSelectedLanguage(language);
    }
    setCode(run.code);
    setStdin(run.stdin);
    setResult(historyToResult(run));
    setViewedRunId(null);
    setError(null);
    setBaseline(run.language, run.code, run.stdin);
    setNotice('Restored code and stdin from the selected run.');
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
      (selectedLanguage.id !== originSnippet.language ||
        code !== originSnippet.code ||
        stdin !== originSnippet.stdin)
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
    if (!selectedLanguage || isSharing || !code.trim()) {
      return;
    }
    if (utf8ByteLength(code) > MAX_CODE_BYTES) {
      setError('Code exceeds the 256KB limit.');
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

    setIsSharing(true);
    setError(null);
    try {
      const payload = {
        language: selectedLanguage.id,
        code,
        stdin,
      };
      const saved = originSnippet
        ? await forkSnippet(originSnippet.slug, payload)
        : await saveSnippet(payload);

      applySnippet(languages, saved);
      navigate(`/s/${saved.slug}`, { replace: true });
      await copyText(shareUrlFor(saved.slug));
      markCopied(originSnippet ? 'Forked and copied a new share link.' : 'Snippet saved. Link copied.');
    } catch (err: any) {
      console.error('Share failed:', err);
      const status = err.response?.status;
      const serverError = err.response?.data?.error;
      if (status === 429) {
        setError(serverError || 'Snippet creation is limited to 20 per hour. Try again later.');
      } else {
        setError(serverError || err.message || 'Failed to save snippet. Please try again.');
      }
    } finally {
      setIsSharing(false);
    }
  };

  const shareLabel = originSnippet && isDirty ? 'Fork & Share' : 'Share';

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
        canShare={Boolean(selectedLanguage && code.trim())}
        shareLabel={shareLabel}
        theme={theme}
        onThemeToggle={handleThemeToggle}
        historyOpen={historyOpen}
        onToggleHistory={() => setHistoryOpen((open) => !open)}
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
        <div className="flex-1 flex flex-col border-r border-editor-border">
          <div className="px-4 py-2 bg-editor-sidebar border-b border-editor-border text-sm text-gray-400 flex items-center justify-between gap-3">
            {selectedLanguage ? (
              <span>
                {selectedLanguage.name} • {selectedLanguage.extension}
                {originSnippet?.title ? ` • ${originSnippet.title}` : ''}
                {isDirty ? ' • unsaved fork' : ''}
              </span>
            ) : (
              <span>Select a language to start coding</span>
            )}
            {originSnippet && (
              <span className="flex items-center gap-3 text-xs">
                <span className="font-mono text-gray-500">/s/{originSnippet.slug}</span>
                <span className="flex items-center gap-1">
                  <Eye className="w-3.5 h-3.5" />
                  {originSnippet.viewCount}
                </span>
              </span>
            )}
          </div>
          <div className="flex-1">
            <CodeEditor
              code={code}
              onChange={handleCodeChange}
              language={selectedLanguage?.id || 'plaintext'}
              theme={theme}
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
        message="The editor has unsaved changes. Restore will replace the current code and stdin with the exact values from this run."
        confirmLabel="Restore"
        onConfirm={() => {
          if (pendingRestore) {
            applyRestore(pendingRestore);
          }
          setPendingRestore(null);
        }}
        onCancel={() => setPendingRestore(null)}
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
