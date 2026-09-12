import React from 'react';
import { Play, Square, Sun, Moon, RotateCcw, Share2, Check, History } from 'lucide-react';
import { Language, EditorTheme } from '../types';
import LanguageSelector from './LanguageSelector';
import AppNav from './AppNav';

interface ToolbarProps {
  languages: Language[];
  selectedLanguage: Language | null;
  onLanguageSelect: (language: Language) => void;
  onRun: () => void;
  onStop: () => void;
  onReset: () => void;
  onShare: () => void;
  isRunning: boolean;
  isSharing: boolean;
  shareCopied: boolean;
  canShare: boolean;
  shareLabel: string;
  theme: EditorTheme;
  onThemeToggle: () => void;
  historyOpen: boolean;
  onToggleHistory: () => void;
}

const Toolbar: React.FC<ToolbarProps> = ({
  languages,
  selectedLanguage,
  onLanguageSelect,
  onRun,
  onStop,
  onReset,
  onShare,
  isRunning,
  isSharing,
  shareCopied,
  canShare,
  shareLabel,
  theme,
  onThemeToggle,
  historyOpen,
  onToggleHistory,
}) => {
  return (
    <div className="flex items-center justify-between px-4 py-3 bg-editor-sidebar border-b border-editor-border">
      {/* Left section */}
      <div className="flex items-center gap-4">
        <AppNav current="playground" />

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
          onClick={onToggleHistory}
          className={`flex items-center gap-2 px-3 py-2 rounded-md transition-colors ${
            historyOpen ? 'text-white bg-editor-border' : 'text-gray-400 hover:text-white hover:bg-editor-border'
          }`}
          title={historyOpen ? 'Hide run history' : 'Show run history'}
        >
          <History className="w-4 h-4" />
          <span className="hidden sm:inline">History</span>
        </button>

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

        {isRunning ? (
          <button
            onClick={onStop}
            className="flex items-center gap-2 px-4 py-2 rounded-md font-medium transition-colors bg-red-600 hover:bg-red-700 text-white"
            title="Stop the running program"
          >
            <Square className="w-4 h-4" />
            <span>Stop</span>
          </button>
        ) : (
          <button
            onClick={onRun}
            disabled={!selectedLanguage}
            className={`flex items-center gap-2 px-4 py-2 rounded-md font-medium transition-colors ${
              !selectedLanguage
                ? 'bg-gray-600 text-gray-400 cursor-not-allowed'
                : 'bg-green-600 hover:bg-green-700 text-white'
            }`}
            title="Run the current program"
          >
            <Play className="w-4 h-4" />
            <span>Run</span>
          </button>
        )}
      </div>
    </div>
  );
};

export default Toolbar;
