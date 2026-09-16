import { ExecutionRequest, ExecutionResponse, ExecutionStatus, QueueStatus } from '../types';
import { RateLimitedError } from './rateLimit';

export class LiveUnavailableError extends Error {
  constructor(message = 'Live execution is unavailable') {
    super(message);
    this.name = 'LiveUnavailableError';
  }
}

export interface LiveRunHandlers {
  onQueued?: (queue: QueueStatus) => void;
  onStarted?: (executionId: string) => void;
  onStdout: (chunk: string) => void;
  onStderr: (chunk: string) => void;
  onDone: (result: ExecutionResponse) => void;
  onError: (message: string) => void;
}

export interface LiveRunSession {
  sendStdin: (data: string) => void;
  closeStdin: () => void;
  stop: () => void;
  close: () => void;
}

interface LiveServerMessage {
  type: string;
  executionId?: string;
  data?: string;
  status?: ExecutionStatus;
  executionTime?: number;
  output?: string;
  error?: string;
  message?: string;
  position?: number;
  estimatedWaitMs?: number;
  retryAfterSeconds?: number;
  reason?: string;
}

const CONNECT_TIMEOUT_MS = 2000;

export function liveExecutionUrl(): string {
  const api = import.meta.env.VITE_API_URL as string | undefined;
  if (api && /^https?:\/\//i.test(api)) {
    const url = new URL(api);
    const protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${url.host}/ws/execute`;
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws/execute`;
}

export function startLiveRun(
  request: ExecutionRequest,
  handlers: LiveRunHandlers,
): Promise<LiveRunSession> {
  return new Promise((resolve, reject) => {
    openSocket(liveExecutionUrl())
      .then((socket) => {
        let finished = false;
        let resolved = false;

        const finish = (result: ExecutionResponse) => {
          if (finished) {
            return;
          }
          finished = true;
          handlers.onDone(result);
          if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
            socket.close();
          }
        };

        const failAfterStart = (message: string) => {
          finish({
            output: '',
            error: message,
            executionTime: 0,
            status: 'ERROR',
          });
        };

        const session: LiveRunSession = {
          sendStdin: (data: string) => {
            if (socket.readyState === WebSocket.OPEN && !finished) {
              socket.send(JSON.stringify({ type: 'stdin', data }));
            }
          },
          closeStdin: () => {
            if (socket.readyState === WebSocket.OPEN && !finished) {
              socket.send(JSON.stringify({ type: 'eof' }));
            }
          },
          stop: () => {
            if (socket.readyState === WebSocket.OPEN && !finished) {
              socket.send(JSON.stringify({ type: 'stop' }));
            }
          },
          close: () => {
            if (finished) {
              return;
            }
            finished = true;
            if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
              socket.close();
            }
          },
        };

        const accept = () => {
          if (resolved) {
            return;
          }
          resolved = true;
          resolve(session);
        };

        socket.onmessage = (event) => {
          let message: LiveServerMessage;
          try {
            message = JSON.parse(String(event.data));
          } catch {
            if (!resolved) {
              resolved = true;
              finished = true;
              socket.close();
              reject(new Error('Received an invalid live execution message.'));
              return;
            }
            failAfterStart('Received an invalid live execution message.');
            return;
          }

          switch (message.type) {
            case 'queued':
              accept();
              handlers.onQueued?.({
                position: message.position || 1,
                estimatedWaitMs: message.estimatedWaitMs || 0,
              });
              break;
            case 'started':
              accept();
              if (message.executionId) {
                handlers.onStarted?.(message.executionId);
              }
              break;
            case 'rejected': {
              const rejected = new RateLimitedError(
                message.message || 'Too many requests. Try again shortly.',
                message.retryAfterSeconds || 1,
                message.reason || 'RATE_LIMIT',
              );
              if (!resolved) {
                resolved = true;
                finished = true;
                socket.close();
                reject(rejected);
                return;
              }
              failAfterStart(rejected.message);
              return;
            }
            case 'stdout':
              if (message.data) {
                handlers.onStdout(message.data);
              }
              break;
            case 'stderr':
              if (message.data) {
                handlers.onStderr(message.data);
              }
              break;
            case 'done':
              accept();
              finish({
                output: message.output || '',
                error: message.error || '',
                executionTime: message.executionTime || 0,
                status: message.status || 'ERROR',
              });
              break;
            case 'error':
              if (!resolved) {
                resolved = true;
                finished = true;
                socket.close();
                reject(new Error(message.message || 'Live execution failed.'));
                return;
              }
              failAfterStart(message.message || 'Live execution failed.');
              break;
            default:
              break;
          }
        };

        socket.onerror = () => {
          if (!resolved) {
            resolved = true;
            finished = true;
            reject(new LiveUnavailableError('Live execution connection failed.'));
            return;
          }
          failAfterStart('Live execution connection failed.');
        };

        socket.onclose = () => {
          if (finished) {
            return;
          }
          if (!resolved) {
            resolved = true;
            finished = true;
            reject(new LiveUnavailableError('Live execution is unavailable.'));
            return;
          }
          failAfterStart('Live execution disconnected.');
        };

        socket.send(JSON.stringify({
          type: 'start',
          language: request.language,
          code: request.code,
          files: request.files,
          entrypoint: request.entrypoint,
          stdin: request.stdin || '',
        }));
      })
      .catch(reject);
  });
}

function openSocket(url: string): Promise<WebSocket> {
  return new Promise((resolve, reject) => {
    let settled = false;
    let socket: WebSocket;
    try {
      socket = new WebSocket(url);
    } catch (error) {
      reject(new LiveUnavailableError(error instanceof Error ? error.message : 'Live execution is unavailable'));
      return;
    }

    const timer = window.setTimeout(() => {
      if (settled) {
        return;
      }
      settled = true;
      socket.close();
      reject(new LiveUnavailableError('Live execution connection timed out'));
    }, CONNECT_TIMEOUT_MS);

    socket.onopen = () => {
      if (settled) {
        return;
      }
      settled = true;
      window.clearTimeout(timer);
      resolve(socket);
    };

    socket.onerror = () => {
      if (settled) {
        return;
      }
      settled = true;
      window.clearTimeout(timer);
      reject(new LiveUnavailableError('Live execution is unavailable'));
    };
  });
}
