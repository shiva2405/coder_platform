import { GitCompare, PanelLeftClose, RotateCcw, Trash2 } from 'lucide-react';
import { Language, RunHistoryEntry } from '../types';
import { formatDuration, formatRunTimestamp, RUN_STATUSES, statusBadgeClass, statusLabel } from '../services/runStatus';

interface HistorySidebarProps {
  open: boolean;
  runs: RunHistoryEntry[];
  languages: Language[];
  error: string | null;
  viewedRunId: string | null;
  compareIds: string[];
  languageFilter: string;
  statusFilter: string;
  onLanguageFilter: (value: string) => void;
  onStatusFilter: (value: string) => void;
  onSelectRun: (id: string) => void;
  onToggleCompare: (id: string) => void;
  onRestore: (run: RunHistoryEntry) => void;
  onCompare: () => void;
  onClear: () => void;
  onClose: () => void;
}

export default function HistorySidebar({
  open,
  runs,
  languages,
  error,
  viewedRunId,
  compareIds,
  languageFilter,
  statusFilter,
  onLanguageFilter,
  onStatusFilter,
  onSelectRun,
  onToggleCompare,
  onRestore,
  onCompare,
  onClear,
  onClose,
}: HistorySidebarProps) {
  if (!open) {
    return null;
  }

  const languageName = (id: string) => languages.find((language) => language.id === id)?.name || id;
  const languageOptions = Array.from(new Set(runs.map((run) => run.language)));
  const filtered = runs.filter((run) => {
    if (languageFilter && run.language !== languageFilter) {
      return false;
    }
    if (statusFilter && run.status !== statusFilter) {
      return false;
    }
    return true;
  });
  const viewedRun = viewedRunId ? runs.find((run) => run.id === viewedRunId) : undefined;

  return (
    <aside className="flex h-full w-72 shrink-0 flex-col border-r border-editor-border bg-editor-sidebar max-md:absolute max-md:inset-y-0 max-md:left-0 max-md:z-30">
      <div className="flex items-center justify-between border-b border-editor-border px-3 py-2">
        <h2 className="text-sm font-semibold text-white">Run history</h2>
        <button
          type="button"
          onClick={onClose}
          className="rounded-md p-1.5 text-gray-400 hover:bg-editor-border hover:text-white"
          title="Collapse history"
        >
          <PanelLeftClose className="h-4 w-4" />
        </button>
      </div>

      <div className="space-y-2 border-b border-editor-border px-3 py-2">
        <div className="grid grid-cols-2 gap-2">
          <label className="block text-[11px] uppercase tracking-wider text-gray-500">
            Language
            <select
              value={languageFilter}
              onChange={(event) => onLanguageFilter(event.target.value)}
              className="mt-1 w-full rounded-md border border-editor-border bg-editor-bg px-2 py-1.5 text-xs text-gray-200"
            >
              <option value="">All</option>
              {languageOptions.map((id) => (
                <option key={id} value={id}>
                  {languageName(id)}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-[11px] uppercase tracking-wider text-gray-500">
            Status
            <select
              value={statusFilter}
              onChange={(event) => onStatusFilter(event.target.value)}
              className="mt-1 w-full rounded-md border border-editor-border bg-editor-bg px-2 py-1.5 text-xs text-gray-200"
            >
              <option value="">All</option>
              {RUN_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {statusLabel(status)}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={onCompare}
            disabled={compareIds.length !== 2}
            className={`flex flex-1 items-center justify-center gap-1 rounded-md px-2 py-1.5 text-xs ${
              compareIds.length === 2
                ? 'bg-editor-border text-white hover:bg-editor-active'
                : 'cursor-not-allowed text-gray-500'
            }`}
            title="Select two runs to compare"
          >
            <GitCompare className="h-3.5 w-3.5" />
            Compare{compareIds.length ? ` (${compareIds.length}/2)` : ''}
          </button>
          <button
            type="button"
            onClick={onClear}
            disabled={runs.length === 0}
            className={`flex items-center justify-center gap-1 rounded-md px-2 py-1.5 text-xs ${
              runs.length === 0 ? 'cursor-not-allowed text-gray-500' : 'text-red-300 hover:bg-red-900/30'
            }`}
            title="Clear history"
          >
            <Trash2 className="h-3.5 w-3.5" />
            Clear
          </button>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {error && <p className="px-3 py-3 text-xs text-yellow-300">{error}</p>}
        {!error && filtered.length === 0 && (
          <p className="px-3 py-6 text-center text-xs text-gray-500">
            {runs.length === 0
              ? 'No runs yet. Run your code to start a history.'
              : 'No runs match these filters.'}
          </p>
        )}
        {filtered.map((run) => {
          const selected = viewedRunId === run.id;
          const checked = compareIds.includes(run.id);
          return (
            <div
              key={run.id}
              className={`border-b border-editor-border/70 ${selected ? 'bg-editor-active/70' : 'hover:bg-editor-border/40'}`}
            >
              <div className="flex items-start gap-2 px-3 py-2">
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => onToggleCompare(run.id)}
                  className="mt-1 accent-blue-500"
                  title="Select for compare"
                  aria-label={`Select run from ${formatRunTimestamp(run.createdAt)} for compare`}
                />
                <button
                  type="button"
                  onClick={() => onSelectRun(run.id)}
                  className="min-w-0 flex-1 text-left"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className={`rounded px-1.5 py-0.5 text-[10px] uppercase ${statusBadgeClass(run.status)}`}>
                      {statusLabel(run.status)}
                    </span>
                    <span className="text-[11px] text-gray-500">{formatDuration(run.executionTime)}</span>
                  </div>
                  <div className="mt-1 flex items-center justify-between gap-2 text-xs">
                    <span className="truncate text-gray-200">{languageName(run.language)}</span>
                    <span className="shrink-0 text-gray-500">{formatRunTimestamp(run.createdAt)}</span>
                  </div>
                </button>
              </div>
            </div>
          );
        })}
      </div>

      {viewedRun && (
        <div className="border-t border-editor-border px-3 py-3">
          <div className="text-[11px] uppercase tracking-wider text-gray-500">Selected run</div>
          <p className="mt-1 truncate text-xs text-gray-300">
            {viewedRun.stdin ? `stdin: ${viewedRun.stdin.split('\n')[0]}` : 'No stdin'}
          </p>
          <button
            type="button"
            onClick={() => onRestore(viewedRun)}
            className="mt-2 flex w-full items-center justify-center gap-1 rounded-md bg-blue-600 px-2 py-1.5 text-xs font-medium text-white hover:bg-blue-700"
          >
            <RotateCcw className="h-3.5 w-3.5" />
            Restore
          </button>
        </div>
      )}
    </aside>
  );
}
