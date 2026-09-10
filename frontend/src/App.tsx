import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Eye } from 'lucide-react';
import CodeEditor from './components/CodeEditor';
import OutputPanel from './components/OutputPanel';
import Toolbar from './components/Toolbar';
import { Language, ExecutionResponse, EditorTheme, Snippet } from './types';
import { executeCode, forkSnippet, getLanguages, getSnippet, saveSnippet } from './services/api';
import { draftsDiffer, loadDraft, saveDraft } from './services/draftStorage';

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
  const loadedSlugRef = useRef<string | null>(null);
  const copyResetRef = useRef<number | undefined>(undefined);

  const applyLanguage = useCallback((langs: Language[], languageId: string): Language | null => {
    return langs.find((language) => language.id === languageId) || langs[0] || null;
  }, []);

  const applyDefaultEditor = useCallback((langs: Language[]) => {
    const defaultLang = langs.find((language) => language.id === 'python') || langs[0];
    if (!defaultLang) {
      return;
    }
    setSelectedLanguage(defaultLang);
    setCode(defaultLang.sampleCode || defaultSampleCodes[defaultLang.id] || '');
    setStdin('');
    setOriginSnippet(null);
  }, []);

  const applyDraft = useCallback((langs: Language[], languageId: string, nextCode: string, nextStdin: string) => {
    const language = applyLanguage(langs, languageId);
    if (language) {
      setSelectedLanguage(language);
    }
    setCode(nextCode);
    setStdin(nextStdin);
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

  const handleRun = useCallback(async () => {
    if (!selectedLanguage || isRunning) return;

    setIsRunning(true);
    setResult(null);
    setError(null);

    try {
      const response = await executeCode({
        language: selectedLanguage.id,
        code,
        stdin,
      });
      setResult(response);
    } catch (err: any) {
      console.error('Execution error:', err);
      setResult({
        output: '',
        error: err.response?.data?.error || err.message || 'Failed to execute code. Please try again.',
        executionTime: 0,
        status: 'ERROR',
      });
    } finally {
      setIsRunning(false);
    }
  }, [selectedLanguage, code, stdin, isRunning]);

  useEffect(() => {
    const handleRunCode = () => {
      if (!isRunning && selectedLanguage) {
        handleRun();
      }
    };
    window.addEventListener('run-code', handleRunCode);
    return () => window.removeEventListener('run-code', handleRunCode);
  }, [isRunning, selectedLanguage, handleRun]);

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
      return;
    }
    if (selectedLanguage) {
      setCode(selectedLanguage.sampleCode || defaultSampleCodes[selectedLanguage.id] || '');
      setStdin('');
      setResult(null);
      setError(null);
    }
  };

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
        onReset={handleReset}
        onShare={handleShare}
        isRunning={isRunning}
        isSharing={isSharing}
        shareCopied={shareCopied}
        canShare={Boolean(selectedLanguage && code.trim())}
        shareLabel={shareLabel}
        theme={theme}
        onThemeToggle={handleThemeToggle}
      />

      <div className="flex-1 flex overflow-hidden">
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
            result={result}
            isLoading={isRunning}
            stdin={stdin}
            onStdinChange={setStdin}
          />
        </div>
      </div>

      <div className="px-4 py-2 bg-editor-sidebar border-t border-editor-border text-xs text-gray-500 flex justify-between">
        <span>
          Time Limit: 30s • Memory Limit: 128MB
        </span>
        <span>
          Press Ctrl+Enter to run
        </span>
      </div>
    </div>
  );
}

export default App;
