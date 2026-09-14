import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import AppNav from '../components/AppNav';
import AuthForm from '../components/AuthForm';
import { useAuth } from '../auth/AuthProvider';

function safeNext(value: string | null): string {
  if (!value || !value.startsWith('/') || value.startsWith('//')) {
    return '/';
  }
  return value;
}

export default function LoginPage() {
  const { user, loading } = useAuth();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const next = safeNext(params.get('next'));
  const error = params.get('error');

  if (!loading && user) {
    return <Navigate to={next} replace />;
  }

  return (
    <div className="h-full overflow-y-auto bg-editor-bg text-gray-200">
      <header className="sticky top-0 z-10 flex items-center justify-between px-4 py-3 bg-editor-sidebar border-b border-editor-border">
        <AppNav current="playground" />
      </header>
      <main className="mx-auto max-w-md px-4 py-12">
        <h1 className="mb-1 text-2xl font-semibold">Sign in</h1>
        <p className="mb-6 text-sm text-gray-400">
          The playground stays anonymous. Sign in to save snippets and manage your workspace.
        </p>
        {error === 'github' && (
          <div className="mb-4 rounded-md bg-red-900/40 px-3 py-2 text-sm text-red-200">
            GitHub login failed. Try again or use email and password.
          </div>
        )}
        <div className="rounded-lg border border-editor-border bg-editor-sidebar p-5">
          <AuthForm nextPath={next} onSuccess={() => navigate(next, { replace: true })} />
        </div>
      </main>
    </div>
  );
}
