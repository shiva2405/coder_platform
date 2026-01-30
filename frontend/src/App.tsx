import React, { useState, useEffect, useCallback } from 'react';
import CodeEditor from './components/CodeEditor';
import OutputPanel from './components/OutputPanel';
import Toolbar from './components/Toolbar';
import { Language, ExecutionResponse, EditorTheme } from './types';
import { executeCode, getLanguages } from './services/api';

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

function App() {
  const [languages, setLanguages] = useState<Language[]>([]);
  const [selectedLanguage, setSelectedLanguage] = useState<Language | null>(null);
  const [code, setCode] = useState<string>('');
  const [result, setResult] = useState<ExecutionResponse | null>(null);
  const [isRunning, setIsRunning] = useState(false);
  const [theme, setTheme] = useState<EditorTheme>('vs-dark');
  const [error, setError] = useState<string | null>(null);

  // Fetch supported languages on mount
  useEffect(() => {
    const fetchLanguages = async () => {
      try {
        const langs = await getLanguages();
        setLanguages(langs);
        // Select Python as default
        const defaultLang = langs.find((l) => l.id === 'python') || langs[0];
        if (defaultLang) {
          setSelectedLanguage(defaultLang);
          setCode(defaultLang.sampleCode || defaultSampleCodes[defaultLang.id] || '');
        }
      } catch (err) {
        console.error('Failed to fetch languages:', err);
        setError('Failed to connect to server. Using offline mode.');
        // Use default languages
        const defaultLangs: Language[] = Object.entries(defaultSampleCodes).map(([id, sampleCode]) => ({
          id,
          name: id.charAt(0).toUpperCase() + id.slice(1),
          extension: `.${id}`,
          sampleCode,
        }));
        setLanguages(defaultLangs);
        const python = defaultLangs.find((l) => l.id === 'python');
        if (python) {
          setSelectedLanguage(python);
          setCode(python.sampleCode);
        }
      }
    };
    fetchLanguages();
  }, []);

  // Listen for run-code event from editor
  useEffect(() => {
    const handleRunCode = () => {
      if (!isRunning && selectedLanguage) {
        handleRun();
      }
    };
    window.addEventListener('run-code', handleRunCode);
    return () => window.removeEventListener('run-code', handleRunCode);
  }, [isRunning, selectedLanguage, code]);

  const handleLanguageSelect = (language: Language) => {
    setSelectedLanguage(language);
    setCode(language.sampleCode || defaultSampleCodes[language.id] || '');
    setResult(null);
    setError(null);
  };

  const handleCodeChange = (value: string | undefined) => {
    setCode(value || '');
  };

  const handleRun = useCallback(async () => {
    if (!selectedLanguage || isRunning) return;

    setIsRunning(true);
    setResult(null);
    setError(null);

    try {
      const response = await executeCode({
        language: selectedLanguage.id,
        code,
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
  }, [selectedLanguage, code, isRunning]);

  const handleReset = () => {
    if (selectedLanguage) {
      setCode(selectedLanguage.sampleCode || defaultSampleCodes[selectedLanguage.id] || '');
      setResult(null);
      setError(null);
    }
  };

  const handleThemeToggle = () => {
    setTheme((prev) => (prev === 'vs-dark' ? 'light' : 'vs-dark'));
  };

  return (
    <div className={`h-screen flex flex-col ${theme === 'light' ? 'bg-white' : 'bg-editor-bg'}`}>
      {/* Error Banner */}
      {error && (
        <div className="px-4 py-2 bg-yellow-600 text-white text-sm text-center">
          {error}
        </div>
      )}

      {/* Toolbar */}
      <Toolbar
        languages={languages}
        selectedLanguage={selectedLanguage}
        onLanguageSelect={handleLanguageSelect}
        onRun={handleRun}
        onReset={handleReset}
        isRunning={isRunning}
        theme={theme}
        onThemeToggle={handleThemeToggle}
      />

      {/* Main Content */}
      <div className="flex-1 flex overflow-hidden">
        {/* Editor Panel */}
        <div className="flex-1 flex flex-col border-r border-editor-border">
          <div className="px-4 py-2 bg-editor-sidebar border-b border-editor-border text-sm text-gray-400">
            {selectedLanguage ? (
              <span>
                {selectedLanguage.name} • {selectedLanguage.extension}
              </span>
            ) : (
              <span>Select a language to start coding</span>
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

        {/* Output Panel */}
        <div className="w-1/3 min-w-[300px] max-w-[600px]">
          <OutputPanel result={result} isLoading={isRunning} />
        </div>
      </div>

      {/* Footer */}
      <div className="px-4 py-2 bg-editor-sidebar border-t border-editor-border text-xs text-gray-500 flex justify-between">
        <span>
          Time Limit: 30s • Memory Limit: 1MB
        </span>
        <span>
          Press Ctrl+Enter to run
        </span>
      </div>
    </div>
  );
}

export default App;
