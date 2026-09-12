import { AlertCircle, CheckCircle, Clock, EyeOff, AlertTriangle } from 'lucide-react';
import { JudgeResult, Verdict } from '../types';

interface JudgeResultsProps {
  result: JudgeResult | null;
  isLoading: boolean;
  mode: 'samples' | 'submit';
}

const verdictLabel: Record<Verdict, string> = {
  ACCEPTED: 'Accepted',
  WRONG_ANSWER: 'Wrong Answer',
  TIME_LIMIT_EXCEEDED: 'Time Limit Exceeded',
  MEMORY_LIMIT_EXCEEDED: 'Memory Limit Exceeded',
  RUNTIME_ERROR: 'Runtime Error',
  COMPILE_ERROR: 'Compilation Error',
  ERROR: 'Error',
};

const verdictColor: Record<Verdict, string> = {
  ACCEPTED: 'text-green-400 bg-green-900/30 border-green-700',
  WRONG_ANSWER: 'text-red-400 bg-red-900/30 border-red-700',
  TIME_LIMIT_EXCEEDED: 'text-yellow-400 bg-yellow-900/30 border-yellow-700',
  MEMORY_LIMIT_EXCEEDED: 'text-orange-400 bg-orange-900/30 border-orange-700',
  RUNTIME_ERROR: 'text-red-400 bg-red-900/30 border-red-700',
  COMPILE_ERROR: 'text-red-400 bg-red-900/30 border-red-700',
  ERROR: 'text-red-400 bg-red-900/30 border-red-700',
};

function VerdictIcon({ verdict }: { verdict: Verdict }) {
  switch (verdict) {
    case 'ACCEPTED':
      return <CheckCircle className="w-4 h-4" />;
    case 'TIME_LIMIT_EXCEEDED':
      return <Clock className="w-4 h-4" />;
    case 'MEMORY_LIMIT_EXCEEDED':
      return <AlertTriangle className="w-4 h-4" />;
    default:
      return <AlertCircle className="w-4 h-4" />;
  }
}

const JudgeResults = ({ result, isLoading, mode }: JudgeResultsProps) => {
  if (isLoading) {
    return (
      <div className="flex items-center gap-3 p-4 text-gray-400">
        <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-blue-500" />
        <span>{mode === 'samples' ? 'Running sample tests...' : 'Judging submission...'}</span>
      </div>
    );
  }

  if (!result) {
    return (
      <div className="p-4 text-sm text-gray-500">
        Run samples to compare against the visible tests, then submit to judge hidden cases.
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3 p-4">
      <div className={`flex flex-wrap items-center justify-between gap-2 rounded-md border px-3 py-2 ${verdictColor[result.verdict]}`}>
        <div className="flex items-center gap-2 font-semibold">
          <VerdictIcon verdict={result.verdict} />
          <span>{verdictLabel[result.verdict]}</span>
        </div>
        <div className="text-xs text-gray-300">
          {result.passedCount}/{result.totalCount} passed
          {result.maxScore > 0 ? ` • ${result.score}/${result.maxScore} pts` : ''}
          {` • ${result.runtimeMs}ms`}
        </div>
      </div>

      {result.compileError && (
        <pre className="whitespace-pre-wrap text-sm text-red-300 bg-red-900/20 p-3 rounded-md overflow-x-auto">
          {result.compileError}
        </pre>
      )}

      {result.cases.map((testCase) => (
        <div key={testCase.index} className="rounded-md border border-editor-border bg-black/20 overflow-hidden">
          <div className="flex items-center justify-between px-3 py-2 bg-editor-sidebar text-sm">
            <span className="flex items-center gap-2">
              {testCase.sample ? `Sample ${testCase.index}` : (
                <span className="flex items-center gap-1 text-gray-400">
                  <EyeOff className="w-3.5 h-3.5" />
                  Hidden test {testCase.index}
                </span>
              )}
            </span>
            <span className={`flex items-center gap-1 text-xs ${verdictColor[testCase.verdict].split(' ')[0]}`}>
              <VerdictIcon verdict={testCase.verdict} />
              {verdictLabel[testCase.verdict]}
              <span className="text-gray-500">• {testCase.runtimeMs}ms</span>
            </span>
          </div>
          {testCase.sample && (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-2 p-3 text-xs font-mono">
              <div>
                <div className="uppercase tracking-wider text-gray-500 mb-1">Input</div>
                <pre className="whitespace-pre-wrap bg-black/30 p-2 rounded min-h-[2.5rem]">{testCase.input ?? ''}</pre>
              </div>
              <div>
                <div className="uppercase tracking-wider text-gray-500 mb-1">Expected</div>
                <pre className="whitespace-pre-wrap bg-black/30 p-2 rounded min-h-[2.5rem]">{testCase.expectedOutput ?? ''}</pre>
              </div>
              <div>
                <div className="uppercase tracking-wider text-gray-500 mb-1">Actual</div>
                <pre className={`whitespace-pre-wrap p-2 rounded min-h-[2.5rem] ${
                  testCase.verdict === 'WRONG_ANSWER' ? 'bg-red-900/20 text-red-200' : 'bg-black/30'
                }`}>
                  {testCase.actualOutput ?? ''}
                </pre>
              </div>
              {testCase.error && (
                <div className="md:col-span-3">
                  <div className="uppercase tracking-wider text-red-400 mb-1">Error</div>
                  <pre className="whitespace-pre-wrap bg-red-900/20 text-red-200 p-2 rounded">{testCase.error}</pre>
                </div>
              )}
            </div>
          )}
        </div>
      ))}
    </div>
  );
};

export default JudgeResults;
