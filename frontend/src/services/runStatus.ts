import { ExecutionStatus } from '../types';

export const RUN_STATUSES: ExecutionStatus[] = [
  'SUCCESS',
  'COMPILE_ERROR',
  'RUNTIME_ERROR',
  'TIMEOUT',
  'MEMORY_EXCEEDED',
  'STOPPED',
  'ERROR',
];

export function statusLabel(status: ExecutionStatus): string {
  switch (status) {
    case 'SUCCESS':
      return 'Success';
    case 'COMPILE_ERROR':
      return 'Compile error';
    case 'RUNTIME_ERROR':
      return 'Runtime error';
    case 'TIMEOUT':
      return 'Timeout';
    case 'MEMORY_EXCEEDED':
      return 'Memory exceeded';
    case 'STOPPED':
      return 'Stopped';
    case 'ERROR':
      return 'Error';
    default:
      return status;
  }
}

export function statusTextClass(status: ExecutionStatus): string {
  switch (status) {
    case 'SUCCESS':
      return 'text-green-500';
    case 'COMPILE_ERROR':
    case 'RUNTIME_ERROR':
    case 'ERROR':
      return 'text-red-500';
    case 'TIMEOUT':
      return 'text-yellow-500';
    case 'STOPPED':
      return 'text-gray-300';
    case 'MEMORY_EXCEEDED':
      return 'text-orange-500';
    default:
      return 'text-gray-400';
  }
}

export function statusBadgeClass(status: ExecutionStatus): string {
  switch (status) {
    case 'SUCCESS':
      return 'bg-green-900/40 text-green-300';
    case 'COMPILE_ERROR':
    case 'RUNTIME_ERROR':
    case 'ERROR':
      return 'bg-red-900/40 text-red-300';
    case 'TIMEOUT':
      return 'bg-yellow-900/40 text-yellow-300';
    case 'STOPPED':
      return 'bg-gray-800 text-gray-300';
    case 'MEMORY_EXCEEDED':
      return 'bg-orange-900/40 text-orange-300';
    default:
      return 'bg-gray-800 text-gray-300';
  }
}

export function formatRunTimestamp(ts: number): string {
  const date = new Date(ts);
  const now = new Date();
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
  }
  return date.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export function formatDuration(ms: number): string {
  if (ms < 1000) {
    return `${ms}ms`;
  }
  return `${(ms / 1000).toFixed(2)}s`;
}
