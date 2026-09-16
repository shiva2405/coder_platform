import { Star, X } from 'lucide-react';
import { fileName } from '../services/projectFiles';

interface EditorTabsProps {
  tabs: string[];
  activePath: string;
  entrypoint: string;
  onSelect: (path: string) => void;
  onClose: (path: string) => void;
}

export default function EditorTabs({ tabs, activePath, entrypoint, onSelect, onClose }: EditorTabsProps) {
  if (tabs.length === 0) {
    return null;
  }

  return (
    <div className="flex overflow-x-auto border-b border-editor-border bg-editor-sidebar">
      {tabs.map((path) => {
        const active = path === activePath;
        return (
          <div
            key={path}
            className={`group flex min-w-0 items-center border-r border-editor-border ${
              active ? 'bg-editor-bg text-white' : 'text-gray-400 hover:bg-editor-border/60 hover:text-white'
            }`}
          >
            <button
              type="button"
              onClick={() => onSelect(path)}
              className="flex min-w-0 items-center gap-1.5 px-3 py-2 text-sm"
              title={path}
            >
              {path === entrypoint && <Star className="h-3 w-3 shrink-0 fill-amber-400 text-amber-400" />}
              <span className="truncate">{fileName(path)}</span>
            </button>
            {tabs.length > 1 && (
              <button
                type="button"
                onClick={() => onClose(path)}
                className="pr-2 text-gray-500 hover:text-white"
                aria-label={`Close ${fileName(path)}`}
              >
                <X className="h-3.5 w-3.5" />
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}
