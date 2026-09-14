import AuthForm from './AuthForm';

interface LoginModalProps {
  open: boolean;
  nextPath?: string;
  message?: string;
  onClose: () => void;
}

export default function LoginModal({ open, nextPath = '/', message, onClose }: LoginModalProps) {
  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="login-modal-title"
        className="w-full max-w-md rounded-lg border border-editor-border bg-editor-sidebar p-5 shadow-xl"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="login-modal-title" className="text-lg font-semibold text-white">
          Sign in to continue
        </h2>
        <p className="mt-2 mb-4 text-sm text-gray-300">
          {message || 'Saving and modifying snippets requires an account. The playground stays available without signing in.'}
        </p>
        <AuthForm nextPath={nextPath} onSuccess={onClose} />
        <button
          type="button"
          onClick={onClose}
          className="mt-4 w-full rounded-md px-3 py-2 text-sm text-gray-300 hover:bg-editor-border hover:text-white"
        >
          Cancel
        </button>
      </div>
    </div>
  );
}
