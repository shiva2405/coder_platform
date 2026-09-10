import React from 'react';
import { Link } from 'react-router-dom';
import { Play, Sun, Moon, RotateCcw, Share2, Check } from 'lucide-react';
import { Language, EditorTheme } from '../types';
import LanguageSelector from './LanguageSelector';

interface ToolbarProps {
  languages: Language[];
  selectedLanguage: Language | null;
  onLanguageSelect: (language: Language) => void;
  onRun: () => void;
  onReset: () => void;
  onShare: () => void;
  isRunning: boolean;
  isSharing: boolean;
  shareCopied: boolean;
  canShare: boolean;
  shareLabel: string;
  theme: EditorTheme;
  onThemeToggle: () => void;
}

const Toolbar: React.FC<ToolbarProps> = ({
  languages,
  selectedLanguage,
  onLanguageSelect,
  onRun,
  onReset,
  onShare,
  isRunning,
  isSharing,
  shareCopied,
  canShare,
  shareLabel,
  theme,
  onThemeToggle,
}) => {
  return (
    <div className="flex items-center justify-between px-4 py-3 bg-editor-sidebar border-b border-editor-border">
      {/* Left section */}
      <div className="flex items-center gap-4">
        <Link to="/" className="flex items-center gap-2 hover:opacity-90">
          <div className="w-8 h-8 bg-gradient-to-br from-blue-500 to-purple-600 rounded-lg flex items-center justify-center">
            <span className="text-white font-bold text-sm">&lt;/&gt;</span>
          </div>
          <span className="font-semibold text-lg">Coder Platform</span>
        </Link>

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
          onClick={onShare}
          disabled={!canShare || isSharing}
          className={`flex items-center gap-2 px-3 py-2 rounded-md transition-colors ${
            !canShare || isSharing
              ? 'text-gray-500 cursor-not-allowed'
              : shareCopied
                ? 'text-green-400 bg-editor-border'
                : 'text-gray-400 hover:text-white hover:bg-editor-border'
          }`}
          title={shareLabel}
        >
          {shareCopied ? <Check className="w-4 h-4" /> : <Share2 className="w-4 h-4" />}
          <span className="hidden sm:inline">{isSharing ? 'Sharing...' : shareCopied ? 'Copied!' : shareLabel}</span>
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
