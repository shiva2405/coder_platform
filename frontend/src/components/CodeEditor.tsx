import React from 'react';
import Editor, { OnMount } from '@monaco-editor/react';
import { EditorTheme } from '../types';

interface CodeEditorProps {
  code: string;
  onChange: (value: string | undefined) => void;
  language: string;
  theme: EditorTheme;
}

const getMonacoLanguage = (languageId: string): string => {
  const languageMap: Record<string, string> = {
    java: 'java',
    python: 'python',
    javascript: 'javascript',
    typescript: 'typescript',
    c: 'c',
    cpp: 'cpp',
    go: 'go',
    rust: 'rust',
    ruby: 'ruby',
    php: 'php',
    kotlin: 'kotlin',
    swift: 'swift',
    perl: 'perl',
    bash: 'shell',
  };
  return languageMap[languageId] || 'plaintext';
};

const CodeEditor: React.FC<CodeEditorProps> = ({ code, onChange, language, theme }) => {
  const handleEditorDidMount: OnMount = (editor, monaco) => {
    // Configure editor settings
    editor.updateOptions({
      fontSize: 14,
      fontFamily: "'Fira Code', 'Cascadia Code', 'JetBrains Mono', Menlo, Monaco, 'Courier New', monospace",
      fontLigatures: true,
      minimap: { enabled: true },
      scrollBeyondLastLine: false,
      automaticLayout: true,
      tabSize: 4,
      insertSpaces: true,
      wordWrap: 'on',
      lineNumbers: 'on',
      renderLineHighlight: 'all',
      cursorBlinking: 'smooth',
      cursorSmoothCaretAnimation: 'on',
      smoothScrolling: true,
      bracketPairColorization: { enabled: true },
      guides: {
        bracketPairs: true,
        indentation: true,
      },
      suggest: {
        showKeywords: true,
        showSnippets: true,
        showFunctions: true,
        showVariables: true,
      },
    });

    // Add keyboard shortcut for running code (Ctrl/Cmd + Enter)
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      // Dispatch custom event to trigger code execution
      window.dispatchEvent(new CustomEvent('run-code'));
    });

    // Focus editor
    editor.focus();
  };

  return (
    <div className="h-full w-full">
      <Editor
        height="100%"
        language={getMonacoLanguage(language)}
        value={code}
        onChange={onChange}
        theme={theme}
        onMount={handleEditorDidMount}
        options={{
          automaticLayout: true,
          scrollBeyondLastLine: false,
          minimap: { enabled: true },
          fontSize: 14,
          lineNumbers: 'on',
          renderLineHighlight: 'all',
          tabSize: 4,
          insertSpaces: true,
          wordWrap: 'on',
        }}
        loading={
          <div className="flex items-center justify-center h-full bg-editor-bg">
            <div className="text-gray-400">Loading editor...</div>
          </div>
        }
      />
    </div>
  );
};

export default CodeEditor;
