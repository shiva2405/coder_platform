import { FormEvent, useEffect, useRef } from 'react';

interface NameDialogProps {
  open: boolean;
  title: string;
  message?: string;
  label: string;
  confirmLabel: string;
  initialValue?: string;
  error?: string | null;
  onSubmit: (value: string) => void;
  onCancel: () => void;
}

export default function NameDialog({
  open,
  title,
  message,
  label,
  confirmLabel,
  initialValue = '',
  error,
  onSubmit,
  onCancel,
}: NameDialogProps) {
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    const timer = window.setTimeout(() => {
      inputRef.current?.focus();
      inputRef.current?.select();
    }, 0);
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onCancel();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [open, onCancel, initialValue]);

  if (!open) {
    return null;
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit(inputRef.current?.value ?? '');
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4" onClick={onCancel}>
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="name-dialog-title"
        className="w-full max-w-md rounded-lg border border-editor-border bg-editor-sidebar p-5 shadow-xl"
        onClick={(event) => event.stopPropagation()}
        onSubmit={handleSubmit}
      >
        <h2 id="name-dialog-title" className="text-lg font-semibold text-white">
          {title}
        </h2>
        {message && <p className="mt-2 text-sm text-gray-300">{message}</p>}
        <label className="mt-4 block text-xs uppercase tracking-wide text-gray-400">
          {label}
          <input
            ref={inputRef}
            name="path"
            defaultValue={initialValue}
            className="mt-1 w-full rounded-md border border-editor-border bg-editor-bg px-3 py-2 text-sm text-white outline-none focus:border-blue-500"
          />
        </label>
        {error && <p className="mt-2 text-sm text-red-400">{error}</p>}
        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-md px-3 py-2 text-sm text-gray-300 hover:bg-editor-border hover:text-white"
          >
            Cancel
          </button>
          <button
            type="submit"
            className="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            {confirmLabel}
          </button>
        </div>
      </form>
    </div>
  );
}
