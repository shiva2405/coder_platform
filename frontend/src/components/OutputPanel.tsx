import React, { useEffect, useRef, useState } from 'react';
import { Terminal, AlertCircle, CheckCircle, Clock, AlertTriangle, RotateCcw, Send } from 'lucide-react';
import { ExecutionResponse, QueueStatus, RunHistoryEntry } from '../types';
import { formatWait } from '../services/rateLimit';
import { formatRunTimestamp } from '../services/runStatus';

interface OutputPanelProps {
  result: ExecutionResponse | null;
  isLoading: boolean;
  live: boolean;
  liveOutput: string;
  liveError: string;
  queue?: QueueStatus | null;
  stdin: string;
  onStdinChange: (value: string) => void;
  interactive: boolean;
  inputClosed?: boolean;
  onSendInput?: (line: string) => void;
  onCloseInput?: () => void;
  viewedRun?: RunHistoryEntry | null;
  onRestoreRun?: () => void;
  onShowLatest?: () => void;
}

const OutputPanel: React.FC<OutputPanelProps> = ({
  result,
  isLoading,
  live,
  liveOutput,
  liveError,
  queue = null,
  stdin,
  onStdinChange,
  interactive,
  inputClosed = false,
  onSendInput,
  onCloseInput,
  viewedRun = null,
  onRestoreRun,
  onShowLatest,
}) => {
  const outputRef = useRef<HTMLDivElement>(null);
  const [inputLine, setInputLine] = useState('');

  const displayOutput = live || isLoading ? liveOutput : result?.output || '';
  const displayError = live || isLoading ? liveError : result?.error || '';
  const showLive = live || isLoading;

  useEffect(() => {
    if (outputRef.current) {
      outputRef.current.scrollTop = outputRef.current.scrollHeight;
    }
  }, [displayOutput, displayError, showLive]);

  const queued = Boolean(queue && queue.position > 0);

  const getStatusIcon = () => {
    if (queued) return <Clock className="w-5 h-5 text-amber-400" />;
    if (showLive) return <Terminal className="w-5 h-5 text-blue-400" />;
    if (!result) return <Terminal className="w-5 h-5 text-gray-400" />;

    switch (result.status) {
      case 'SUCCESS':
        return <CheckCircle className="w-5 h-5 text-green-500" />;
      case 'COMPILE_ERROR':
      case 'RUNTIME_ERROR':
      case 'ERROR':
        return <AlertCircle className="w-5 h-5 text-red-500" />;
      case 'TIMEOUT':
        return <Clock className="w-5 h-5 text-yellow-500" />;
      case 'MEMORY_EXCEEDED':
        return <AlertTriangle className="w-5 h-5 text-orange-500" />;
      case 'STOPPED':
        return <AlertCircle className="w-5 h-5 text-gray-400" />;
      default:
        return <Terminal className="w-5 h-5 text-gray-400" />;
    }
  };

  const getStatusText = () => {
    if (queued && queue) {
      return `Queued — position ${queue.position} · about ${formatWait(queue.estimatedWaitMs)}`;
    }
    if (showLive) return live ? 'Running...' : 'Running (buffered)...';
    if (!result) return 'Output';

    switch (result.status) {
      case 'SUCCESS':
        return 'Execution Successful';
      case 'COMPILE_ERROR':
        return 'Compilation Error';
      case 'RUNTIME_ERROR':
        return 'Runtime Error';
      case 'TIMEOUT':
        return 'Time Limit Exceeded';
      case 'MEMORY_EXCEEDED':
        return 'Memory Limit Exceeded';
      case 'STOPPED':
        return 'Stopped';
      case 'ERROR':
        return 'Error';
      default:
        return 'Output';
    }
  };

  const getStatusColor = () => {
    if (queued) return 'text-amber-300';
    if (showLive) return 'text-blue-400';
    if (!result) return 'text-gray-400';

    switch (result.status) {
      case 'SUCCESS':
        return 'text-green-500';
      case 'COMPILE_ERROR':
      case 'RUNTIME_ERROR':
      case 'ERROR':
        return 'text-red-500';
      case 'TIMEOUT':
        return 'text-yellow-500';
      case 'MEMORY_EXCEEDED':
        return 'text-orange-500';
      case 'STOPPED':
        return 'text-gray-300';
      default:
        return 'text-gray-400';
    }
  };

  const sendInput = () => {
    if (!interactive || inputClosed || !onSendInput) {
      return;
    }
    onSendInput(inputLine.endsWith('\n') ? inputLine : `${inputLine}\n`);
    setInputLine('');
  };

  return (
    <div className="h-full flex flex-col bg-editor-bg">
      <div className="flex items-center justify-between px-4 py-2 bg-editor-sidebar border-b border-editor-border">
        <div className="flex items-center gap-2">
          {getStatusIcon()}
          <span className={`font-medium ${getStatusColor()}`}>
            {getStatusText()}
          </span>
        </div>
        {result && !showLive && (
          <span className="text-sm text-gray-400">
            Execution time: {result.executionTime}ms
          </span>
        )}
      </div>

      {viewedRun && !showLive && (
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-editor-border bg-blue-950/40 px-4 py-2 text-xs text-blue-100">
          <span>
            Viewing run from {formatRunTimestamp(viewedRun.createdAt)}
            {viewedRun.outputTruncated ? ' • output truncated to 500 characters' : ''}
          </span>
          <div className="flex items-center gap-2">
            {onRestoreRun && (
              <button
                type="button"
                onClick={onRestoreRun}
                className="flex items-center gap-1 rounded-md bg-blue-600 px-2 py-1 text-white hover:bg-blue-700"
              >
                <RotateCcw className="h-3 w-3" />
                Restore
              </button>
            )}
            {onShowLatest && (
              <button
                type="button"
                onClick={onShowLatest}
                className="rounded-md px-2 py-1 text-blue-200 hover:bg-blue-900/70"
              >
                Latest
              </button>
            )}
          </div>
        </div>
      )}

      <div className="border-b border-editor-border px-4 py-2">
        <label className="block text-xs uppercase tracking-wider text-gray-500 mb-2">
          Standard Input
        </label>
        <textarea
          value={stdin}
          onChange={(event) => onStdinChange(event.target.value)}
          placeholder={interactive ? 'Sent when the program starts. Use the input box below while it is running.' : 'Optional stdin passed to the program'}
          disabled={isLoading}
          className="w-full h-20 resize-y bg-black/30 text-gray-200 text-sm font-mono p-2 rounded-md border border-editor-border focus:outline-none focus:border-blue-500 disabled:opacity-60"
        />
      </div>

      <div ref={outputRef} className="flex-1 overflow-auto p-4">
        {showLive || result ? (
          <div className="font-mono text-sm">
            {(displayOutput || showLive || (result && !displayError)) && (
              <div className="mb-4">
                <div className="text-green-400 mb-2 text-xs uppercase tracking-wider">
                  Standard Output
                </div>
                <pre className="whitespace-pre-wrap text-gray-200 bg-black/30 p-3 rounded-md overflow-x-auto min-h-[3rem]">
                  {displayOutput || (queued ? 'Waiting for an execution slot...' : showLive ? 'Waiting for output...' : '(No output)')}
                </pre>
              </div>
            )}
            {displayError && (
              <div>
                <div className="text-red-400 mb-2 text-xs uppercase tracking-wider">
                  {result?.status === 'COMPILE_ERROR' ? 'Compilation Error' : 'Error Output'}
                </div>
                <pre className="whitespace-pre-wrap text-red-300 bg-red-900/20 p-3 rounded-md overflow-x-auto">
                  {displayError}
                </pre>
              </div>
            )}
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center h-full text-gray-500">
            <Terminal className="w-12 h-12 mb-4 opacity-50" />
            <p>Run your code to see the output</p>
            <p className="text-sm mt-2 text-gray-600">
              Press <kbd className="px-2 py-1 bg-editor-sidebar rounded">Ctrl</kbd> +
              <kbd className="px-2 py-1 bg-editor-sidebar rounded ml-1">Enter</kbd> to run
            </p>
          </div>
        )}
      </div>

      {interactive && (
        <div className="border-t border-editor-border bg-editor-sidebar px-4 py-2">
          <label className="block text-xs uppercase tracking-wider text-gray-500 mb-2">
            Interactive input
          </label>
          <div className="flex items-center gap-2">
            <input
              type="text"
              value={inputLine}
              onChange={(event) => setInputLine(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault();
                  sendInput();
                }
              }}
              disabled={inputClosed}
              placeholder={inputClosed ? 'Standard input is closed' : 'Type a line and press Enter'}
              className="flex-1 bg-black/30 text-gray-200 text-sm font-mono px-2 py-1.5 rounded-md border border-editor-border focus:outline-none focus:border-blue-500 disabled:opacity-60"
            />
            <button
              type="button"
              onClick={sendInput}
              disabled={inputClosed}
              className="flex items-center gap-1 rounded-md bg-blue-600 px-2 py-1.5 text-xs text-white hover:bg-blue-700 disabled:bg-gray-600 disabled:text-gray-400"
            >
              <Send className="h-3 w-3" />
              Send
            </button>
            {onCloseInput && (
              <button
                type="button"
                onClick={onCloseInput}
                disabled={inputClosed}
                className="rounded-md px-2 py-1.5 text-xs text-gray-300 hover:bg-editor-border disabled:opacity-50"
              >
                Close input
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default OutputPanel;
