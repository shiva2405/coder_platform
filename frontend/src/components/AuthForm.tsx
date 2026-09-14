import { FormEvent, useEffect, useState } from 'react';
import { Github } from 'lucide-react';
import { getAuthProviders } from '../services/api';
import { useAuth } from '../auth/AuthProvider';

interface AuthFormProps {
  nextPath?: string;
  onSuccess?: () => void;
}

export default function AuthForm({ nextPath = '/', onSuccess }: AuthFormProps) {
  const { login, register, loginWithGithub } = useAuth();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [localEnabled, setLocalEnabled] = useState(true);
  const [githubEnabled, setGithubEnabled] = useState(false);

  useEffect(() => {
    getAuthProviders()
      .then((providers) => {
        setLocalEnabled(providers.local);
        setGithubEnabled(providers.github);
      })
      .catch(() => {
        setLocalEnabled(true);
        setGithubEnabled(false);
      });
  }, []);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'register') {
        await register(email, password, name);
      } else {
        await login(email, password);
      }
      onSuccess?.();
    } catch (err: any) {
      setError(err.response?.data?.error || err.message || 'Authentication failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      {githubEnabled && (
        <button
          type="button"
          onClick={() => loginWithGithub(nextPath)}
          className="flex w-full items-center justify-center gap-2 rounded-md bg-white px-3 py-2 text-sm font-medium text-gray-900 hover:bg-gray-100"
        >
          <Github className="h-4 w-4" />
          Continue with GitHub
        </button>
      )}

      {githubEnabled && localEnabled && (
        <div className="flex items-center gap-3 text-xs text-gray-500">
          <div className="h-px flex-1 bg-editor-border" />
          or
          <div className="h-px flex-1 bg-editor-border" />
        </div>
      )}

      {localEnabled && (
        <form onSubmit={handleSubmit} className="space-y-3">
          {mode === 'register' && (
            <label className="block text-sm">
              <span className="mb-1 block text-gray-400">Name</span>
              <input
                value={name}
                onChange={(event) => setName(event.target.value)}
                required
                className="w-full rounded-md border border-editor-border bg-editor-bg px-3 py-2 text-white outline-none focus:border-blue-500"
              />
            </label>
          )}
          <label className="block text-sm">
            <span className="mb-1 block text-gray-400">Email</span>
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
              className="w-full rounded-md border border-editor-border bg-editor-bg px-3 py-2 text-white outline-none focus:border-blue-500"
            />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block text-gray-400">Password</span>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              minLength={mode === 'register' ? 8 : undefined}
              className="w-full rounded-md border border-editor-border bg-editor-bg px-3 py-2 text-white outline-none focus:border-blue-500"
            />
          </label>
          {error && <div className="rounded-md bg-red-900/40 px-3 py-2 text-sm text-red-200">{error}</div>}
          <button
            type="submit"
            disabled={busy}
            className="w-full rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-600"
          >
            {busy ? 'Please wait...' : mode === 'register' ? 'Create account' : 'Sign in'}
          </button>
        </form>
      )}

      {localEnabled && (
        <button
          type="button"
          onClick={() => {
            setMode((current) => (current === 'login' ? 'register' : 'login'));
            setError(null);
          }}
          className="w-full text-sm text-gray-400 hover:text-white"
        >
          {mode === 'login' ? 'Need an account? Register' : 'Already have an account? Sign in'}
        </button>
      )}

      {!localEnabled && !githubEnabled && (
        <p className="text-sm text-gray-400">No login providers are configured.</p>
      )}
    </div>
  );
}
