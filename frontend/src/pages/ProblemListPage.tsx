import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Clock, Database } from 'lucide-react';
import AppNav from '../components/AppNav';
import UserMenu from '../components/UserMenu';
import { listProblems } from '../services/api';
import { Difficulty, ProblemSummary } from '../types';

const difficultyClass: Record<Difficulty, string> = {
  EASY: 'bg-green-900/40 text-green-300',
  MEDIUM: 'bg-yellow-900/40 text-yellow-300',
  HARD: 'bg-red-900/40 text-red-300',
};

function formatMemory(bytes: number): string {
  return `${Math.round(bytes / (1024 * 1024))}MB`;
}

export default function ProblemListPage() {
  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      try {
        setProblems(await listProblems());
      } catch (err) {
        console.error(err);
        setError('Failed to load problems. Is the backend running?');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  return (
    <div className="h-full overflow-y-auto bg-editor-bg text-gray-200">
      <header className="sticky top-0 z-10 flex items-center justify-between px-4 py-3 bg-editor-sidebar border-b border-editor-border">
        <AppNav current="problems" />
        <UserMenu />
      </header>

      <main className="max-w-5xl mx-auto px-4 py-8">
        <h1 className="text-2xl font-semibold mb-1">Problems</h1>
        <p className="text-sm text-gray-400 mb-6">
          Solve a problem, run the sample tests, then submit for hidden-case judging.
        </p>

        {error && (
          <div className="mb-4 px-4 py-2 rounded-md bg-yellow-700/40 text-yellow-100 text-sm">{error}</div>
        )}

        {loading ? (
          <div className="text-gray-500">Loading problems...</div>
        ) : problems.length === 0 ? (
          <div className="text-gray-500">No problems yet.</div>
        ) : (
          <div className="overflow-hidden rounded-lg border border-editor-border">
            <table className="w-full text-sm">
              <thead className="bg-editor-sidebar text-gray-400 text-left">
                <tr>
                  <th className="px-4 py-3 font-medium">Title</th>
                  <th className="px-4 py-3 font-medium">Difficulty</th>
                  <th className="px-4 py-3 font-medium hidden sm:table-cell">Tags</th>
                  <th className="px-4 py-3 font-medium hidden md:table-cell">Limits</th>
                  <th className="px-4 py-3 font-medium hidden md:table-cell">Tests</th>
                </tr>
              </thead>
              <tbody>
                {problems.map((problem) => (
                  <tr key={problem.slug} className="border-t border-editor-border hover:bg-white/5">
                    <td className="px-4 py-3">
                      <Link to={`/problems/${problem.slug}`} className="text-blue-300 hover:text-blue-200 font-medium">
                        {problem.title}
                      </Link>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${difficultyClass[problem.difficulty]}`}>
                        {problem.difficulty}
                      </span>
                    </td>
                    <td className="px-4 py-3 hidden sm:table-cell">
                      <div className="flex flex-wrap gap-1">
                        {problem.tags.map((tag) => (
                          <span key={tag} className="px-2 py-0.5 rounded bg-editor-border text-gray-300 text-xs">
                            {tag}
                          </span>
                        ))}
                      </div>
                    </td>
                    <td className="px-4 py-3 hidden md:table-cell text-gray-400">
                      <span className="inline-flex items-center gap-1 mr-3">
                        <Clock className="w-3.5 h-3.5" />
                        {problem.timeLimitMs}ms
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <Database className="w-3.5 h-3.5" />
                        {formatMemory(problem.memoryLimitBytes)}
                      </span>
                    </td>
                    <td className="px-4 py-3 hidden md:table-cell text-gray-400">
                      {problem.sampleCount} sample / {problem.totalTestCases} total
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>
    </div>
  );
}
