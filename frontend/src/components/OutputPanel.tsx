import React from 'react';
import { Terminal, AlertCircle, CheckCircle, Clock, AlertTriangle } from 'lucide-react';
import { ExecutionResponse } from '../types';

interface OutputPanelProps {
  result: ExecutionResponse | null;
  isLoading: boolean;
}

const OutputPanel: React.FC<OutputPanelProps> = ({ result, isLoading }) => {
  const getStatusIcon = () => {
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
      default:
        return <Terminal className="w-5 h-5 text-gray-400" />;
    }
  };

  const getStatusText = () => {
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
      case 'ERROR':
        return 'Error';
      default:
        return 'Output';
    }
  };

  const getStatusColor = () => {
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
      default:
        return 'text-gray-400';
    }
  };

  return (
    <div className="h-full flex flex-col bg-editor-bg">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-2 bg-editor-sidebar border-b border-editor-border">
        <div className="flex items-center gap-2">
          {getStatusIcon()}
          <span className={`font-medium ${getStatusColor()}`}>
            {isLoading ? 'Running...' : getStatusText()}
          </span>
        </div>
        {result && (
          <span className="text-sm text-gray-400">
            Execution time: {result.executionTime}ms
          </span>
        )}
      </div>

      {/* Output Content */}
      <div className="flex-1 overflow-auto p-4">
        {isLoading ? (
          <div className="flex items-center justify-center h-full">
            <div className="flex items-center gap-3">
              <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-500"></div>
              <span className="text-gray-400">Executing code...</span>
            </div>
          </div>
        ) : result ? (
          <div className="font-mono text-sm">
            {result.output && (
              <div className="mb-4">
                <div className="text-green-400 mb-2 text-xs uppercase tracking-wider">
                  Standard Output
                </div>
                <pre className="whitespace-pre-wrap text-gray-200 bg-black/30 p-3 rounded-md overflow-x-auto">
                  {result.output || '(No output)'}
                </pre>
              </div>
            )}
            {result.error && (
              <div>
                <div className="text-red-400 mb-2 text-xs uppercase tracking-wider">
                  {result.status === 'COMPILE_ERROR' ? 'Compilation Error' : 'Error Output'}
                </div>
                <pre className="whitespace-pre-wrap text-red-300 bg-red-900/20 p-3 rounded-md overflow-x-auto">
                  {result.error}
                </pre>
              </div>
            )}
            {!result.output && !result.error && (
              <div className="text-gray-500 italic">
                Program executed successfully with no output.
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
    </div>
  );
};

export default OutputPanel;
