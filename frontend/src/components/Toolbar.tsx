import React from 'react';
import { Play, Sun, Moon, RotateCcw, Settings } from 'lucide-react';
import { Language, EditorTheme } from '../types';
import LanguageSelector from './LanguageSelector';

interface ToolbarProps {
  languages: Language[];
  selectedLanguage: Language | null;
  onLanguageSelect: (language: Language) => void;
  onRun: () => void;
  onReset: () => void;
  isRunning: boolean;
  theme: EditorTheme;
  onThemeToggle: () => void;
}

const Toolbar: React.FC<ToolbarProps> = ({
  languages,
  selectedLanguage,
  onLanguageSelect,
  onRun,
  onReset,
  isRunning,
  theme,
  onThemeToggle,
}) => {
  return (
    <div className="flex items-center justify-between px-4 py-3 bg-editor-sidebar border-b border-editor-border">
      {/* Left section */}
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 bg-gradient-to-br from-blue-500 to-purple-600 rounded-lg flex items-center justify-center">
            <span className="text-white font-bold text-sm">&lt;/&gt;</span>
          </div>
          <span className="font-semibold text-lg">Coder Platform</span>
        </div>

        <div className="h-6 w-px bg-editor-border" />

        <LanguageSelector
          languages={languages}
          selectedLanguage={selectedLanguage}
          onSelect={onLanguageSelect}
        />
      </div>

      {/* Right section */}
      <div className="flex items-center gap-2">
        <button
          onClick={onReset}
          className="flex items-center gap-2 px-3 py-2 text-gray-400 hover:text-white hover:bg-editor-border rounded-md transition-colors"
          title="Reset to sample code"
        >
          <RotateCcw className="w-4 h-4" />
          <span className="hidden sm:inline">Reset</span>
        </button>

        <button
          onClick={onThemeToggle}
          className="flex items-center gap-2 px-3 py-2 text-gray-400 hover:text-white hover:bg-editor-border rounded-md transition-colors"
          title={theme === 'vs-dark' ? 'Switch to light theme' : 'Switch to dark theme'}
        >
          {theme === 'vs-dark' ? (
            <Sun className="w-4 h-4" />
          ) : (
            <Moon className="w-4 h-4" />
          )}
        </button>

        <button
          onClick={onRun}
          disabled={isRunning || !selectedLanguage}
          className={`flex items-center gap-2 px-4 py-2 rounded-md font-medium transition-colors ${
            isRunning || !selectedLanguage
              ? 'bg-gray-600 text-gray-400 cursor-not-allowed'
              : 'bg-green-600 hover:bg-green-700 text-white'
          }`}
        >
          {isRunning ? (
            <>
              <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
              <span>Running...</span>
            </>
          ) : (
            <>
              <Play className="w-4 h-4" />
              <span>Run</span>
            </>
          )}
        </button>
      </div>
    </div>
  );
};

export default Toolbar;
