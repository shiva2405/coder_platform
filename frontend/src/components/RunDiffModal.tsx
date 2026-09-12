import { useEffect } from 'react';
import { DiffEditor } from '@monaco-editor/react';
import { X } from 'lucide-react';
import { EditorTheme, RunHistoryEntry } from '../types';
import { getMonacoLanguage } from '../services/monacoLanguage';
import { formatDuration, formatRunTimestamp, statusBadgeClass, statusLabel } from '../services/runStatus';

interface RunDiffModalProps {
  left: RunHistoryEntry;
  right: RunHistoryEntry;
  theme: EditorTheme;
  onClose: () => void;
}

function RunMeta({ entry, label }: { entry: RunHistoryEntry; label: string }) {
  return (
    <div className="min-w-0">
      <div className="text-xs uppercase tracking-wider text-gray-500">{label}</div>
      <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-gray-200">
        <span className={`rounded px-1.5 py-0.5 text-xs ${statusBadgeClass(entry.status)}`}>
          {statusLabel(entry.status)}
        </span>
        <span>{entry.language}</span>
        <span className="text-gray-500">{formatRunTimestamp(entry.createdAt)}</span>
        <span className="text-gray-500">{formatDuration(entry.executionTime)}</span>
      </div>
    </div>
  );
}

export default function RunDiffModal({ left, right, theme, onClose }: RunDiffModalProps) {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4 py-6" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="run-diff-title"
        className="flex h-[85vh] w-full max-w-6xl flex-col overflow-hidden rounded-lg border border-editor-border bg-editor-bg shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4 border-b border-editor-border bg-editor-sidebar px-4 py-3">
          <div className="min-w-0 flex-1">
            <h2 id="run-diff-title" className="text-base font-semibold text-white">
              Compare runs
            </h2>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <RunMeta entry={left} label="Original (first selected)" />
              <RunMeta entry={right} label="Modified (second selected)" />
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md p-2 text-gray-400 hover:bg-editor-border hover:text-white"
            title="Close compare"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="min-h-0 flex-1">
          <DiffEditor
            height="100%"
            original={left.code}
            modified={right.code}
            originalLanguage={getMonacoLanguage(left.language)}
            modifiedLanguage={getMonacoLanguage(right.language)}
            theme={theme}
            options={{
              readOnly: true,
              renderSideBySide: true,
              automaticLayout: true,
              minimap: { enabled: false },
              fontSize: 13,
              scrollBeyondLastLine: false,
              wordWrap: 'on',
            }}
          />
        </div>
      </div>
    </div>
  );
}
