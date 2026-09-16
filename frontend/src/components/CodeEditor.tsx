import Editor, { OnMount } from '@monaco-editor/react';
import { EditorTheme } from '../types';
import { getMonacoLanguageFromPath } from '../services/monacoLanguage';

interface CodeEditorProps {
  path?: string;
  code: string;
  onChange: (value: string | undefined) => void;
  language: string;
  theme: EditorTheme;
  knownPaths?: string[];
}

const CodeEditor: React.FC<CodeEditorProps> = ({
  path = 'untitled',
  code,
  onChange,
  language,
  theme,
}) => {
  const handleEditorDidMount: OnMount = (editor, monaco) => {
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

    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      window.dispatchEvent(new CustomEvent('run-code'));
    });

    editor.focus();
  };

  return (
    <div className="h-full w-full">
      <Editor
        height="100%"
        path={path}
        language={getMonacoLanguageFromPath(path, language)}
        value={code}
        onChange={onChange}
        theme={theme}
        keepCurrentModel
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
