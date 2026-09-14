import { useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { Search, Trash2 } from 'lucide-react';
import AppNav from '../components/AppNav';
import ConfirmDialog from '../components/ConfirmDialog';
import UserMenu from '../components/UserMenu';
import { useAuth } from '../auth/AuthProvider';
import { deleteSnippet, listMySnippets, updateSnippet } from '../services/api';
import { SnippetSummary, SnippetVisibility } from '../types';

const visibilityClass: Record<SnippetVisibility, string> = {
  PUBLIC: 'bg-green-900/40 text-green-300',
  UNLISTED: 'bg-yellow-900/40 text-yellow-300',
  PRIVATE: 'bg-red-900/40 text-red-300',
};

export default function DashboardPage() {
  const { user, loading } = useAuth();
  const location = useLocation();
  const [snippets, setSnippets] = useState<SnippetSummary[]>([]);
  const [query, setQuery] = useState('');
  const [visibility, setVisibility] = useState<SnippetVisibility | ''>('');
  const [sort, setSort] = useState('updatedAt');
  const [order, setOrder] = useState('desc');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<SnippetSummary | null>(null);

  const load = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await listMySnippets({
        q: query.trim() || undefined,
        visibility: visibility || undefined,
        sort,
        order,
      });
      setSnippets(result.snippets);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to load your snippets.');
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (user) {
      void load();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, visibility, sort, order]);

  const filteredHint = useMemo(() => {
    if (busy) {
      return 'Loading...';
    }
    return `${snippets.length} snippet${snippets.length === 1 ? '' : 's'}`;
  }, [busy, snippets.length]);

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center bg-editor-bg text-gray-400">
        Loading workspace...
      </div>
    );
  }

  if (!user) {
    return <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />;
  }

  return (
    <div className="h-full overflow-y-auto bg-editor-bg text-gray-200">
      <header className="sticky top-0 z-10 flex items-center justify-between px-4 py-3 bg-editor-sidebar border-b border-editor-border">
        <AppNav current="dashboard" />
        <UserMenu />
      </header>

      <main className="mx-auto max-w-5xl px-4 py-8">
        <h1 className="text-2xl font-semibold">Your snippets</h1>
        <p className="mb-6 text-sm text-gray-400">
          Search, sort, change visibility, or delete snippets from your workspace.
        </p>

        <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center">
          <form
            className="flex flex-1 items-center gap-2 rounded-md border border-editor-border bg-editor-sidebar px-3 py-2"
            onSubmit={(event) => {
              event.preventDefault();
              void load();
            }}
          >
            <Search className="h-4 w-4 text-gray-500" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search title, language, or slug"
              className="w-full bg-transparent text-sm text-white outline-none"
            />
          </form>
          <select
            value={visibility}
            onChange={(event) => setVisibility(event.target.value as SnippetVisibility | '')}
            className="rounded-md border border-editor-border bg-editor-sidebar px-3 py-2 text-sm"
          >
            <option value="">All visibility</option>
            <option value="PUBLIC">Public</option>
            <option value="UNLISTED">Unlisted</option>
            <option value="PRIVATE">Private</option>
          </select>
          <select
            value={`${sort}:${order}`}
            onChange={(event) => {
              const [nextSort, nextOrder] = event.target.value.split(':');
              setSort(nextSort);
              setOrder(nextOrder);
            }}
            className="rounded-md border border-editor-border bg-editor-sidebar px-3 py-2 text-sm"
          >
            <option value="updatedAt:desc">Last updated</option>
            <option value="createdAt:desc">Newest</option>
            <option value="createdAt:asc">Oldest</option>
            <option value="title:asc">Title A-Z</option>
            <option value="viewCount:desc">Most viewed</option>
          </select>
        </div>

        <div className="mb-3 text-xs text-gray-500">{filteredHint}</div>
        {error && <div className="mb-4 rounded-md bg-yellow-700/40 px-3 py-2 text-sm text-yellow-100">{error}</div>}

        {snippets.length === 0 && !busy ? (
          <div className="rounded-lg border border-editor-border bg-editor-sidebar px-4 py-8 text-sm text-gray-400">
            No snippets yet. Share from the playground to save one to this workspace.
          </div>
        ) : (
          <div className="overflow-hidden rounded-lg border border-editor-border">
            <table className="w-full text-sm">
              <thead className="bg-editor-sidebar text-left text-gray-400">
                <tr>
                  <th className="px-4 py-3 font-medium">Snippet</th>
                  <th className="px-4 py-3 font-medium">Language</th>
                  <th className="px-4 py-3 font-medium">Visibility</th>
                  <th className="hidden px-4 py-3 font-medium md:table-cell">Updated</th>
                  <th className="px-4 py-3 font-medium"> </th>
                </tr>
              </thead>
              <tbody>
                {snippets.map((snippet) => (
                  <tr key={snippet.slug} className="border-t border-editor-border hover:bg-white/5">
                    <td className="px-4 py-3">
                      <Link to={`/s/${snippet.slug}`} className="font-medium text-blue-300 hover:text-blue-200">
                        {snippet.title || snippet.slug}
                      </Link>
                      <div className="font-mono text-xs text-gray-500">/s/{snippet.slug}</div>
                    </td>
                    <td className="px-4 py-3 capitalize">{snippet.language}</td>
                    <td className="px-4 py-3">
                      <select
                        value={snippet.visibility}
                        onChange={async (event) => {
                          const next = event.target.value as SnippetVisibility;
                          try {
                            const updated = await updateSnippet(snippet.slug, { visibility: next });
                            setSnippets((current) =>
                              current.map((item) => (item.slug === snippet.slug ? { ...item, visibility: updated.visibility } : item)),
                            );
                          } catch (err: any) {
                            setError(err.response?.data?.error || 'Failed to update visibility.');
                          }
                        }}
                        className={`rounded px-2 py-1 text-xs ${visibilityClass[snippet.visibility]}`}
                      >
                        <option value="PUBLIC">PUBLIC</option>
                        <option value="UNLISTED">UNLISTED</option>
                        <option value="PRIVATE">PRIVATE</option>
                      </select>
                    </td>
                    <td className="hidden px-4 py-3 text-gray-400 md:table-cell">
                      {new Date(snippet.updatedAt).toLocaleString()}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button
                        type="button"
                        onClick={() => setPendingDelete(snippet)}
                        className="rounded-md p-2 text-gray-400 hover:bg-red-900/40 hover:text-red-200"
                        title="Delete snippet"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete this snippet?"
        message="This permanently removes the snippet. Anyone with the old link will get a 404."
        confirmLabel="Delete"
        danger
        onConfirm={async () => {
          if (!pendingDelete) {
            return;
          }
          try {
            await deleteSnippet(pendingDelete.slug);
            setSnippets((current) => current.filter((item) => item.slug !== pendingDelete.slug));
          } catch (err: any) {
            setError(err.response?.data?.error || 'Failed to delete snippet.');
          } finally {
            setPendingDelete(null);
          }
        }}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
