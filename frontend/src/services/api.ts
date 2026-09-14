import axios from 'axios';
import {
  AuthProviders,
  ExecutionRequest,
  ExecutionResponse,
  JudgeResult,
  Language,
  ProblemDetail,
  ProblemSummary,
  QueueStatus,
  Snippet,
  SnippetList,
  SnippetRequest,
  SnippetUpdateRequest,
  SnippetVisibility,
  SubmissionSummary,
  User,
  WorkTicket,
} from '../types';
import { RateLimitedError, rateLimitFromAxios } from './rateLimit';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const url = String(error.config?.url || '');
    const authCall = url.includes('/auth/login') || url.includes('/auth/register') || url.includes('/auth/me');
    if (status === 401 && !authCall) {
      window.dispatchEvent(new Event('auth:unauthorized'));
    }
    return Promise.reject(error);
  },
);

export const executeCode = async (
  request: ExecutionRequest,
  signal?: AbortSignal,
  onQueue?: (queue: QueueStatus | null) => void,
): Promise<ExecutionResponse> => {
  try {
    const response = await api.post<WorkTicket<ExecutionResponse>>('/execute', request, { signal });
    return await waitForJob(response.data, signal, onQueue);
  } catch (error) {
    throw rateLimitFromAxios(error) ?? error;
  }
};

export const getLanguages = async (): Promise<Language[]> => {
  const response = await api.get<Language[]>('/languages');
  return response.data;
};

export const checkHealth = async (): Promise<boolean> => {
  try {
    await api.get('/health');
    return true;
  } catch {
    return false;
  }
};

export const saveSnippet = async (request: SnippetRequest): Promise<Snippet> => {
  const response = await api.post<Snippet>('/snippets', request);
  return response.data;
};

export const getSnippet = async (slug: string): Promise<Snippet> => {
  const response = await api.get<Snippet>(`/snippets/${slug}`);
  return response.data;
};

export const forkSnippet = async (slug: string, request: SnippetRequest): Promise<Snippet> => {
  const response = await api.post<Snippet>(`/snippets/${slug}/fork`, request);
  return response.data;
};

export const updateSnippet = async (slug: string, request: SnippetUpdateRequest): Promise<Snippet> => {
  const response = await api.patch<Snippet>(`/snippets/${slug}`, request);
  return response.data;
};

export const deleteSnippet = async (slug: string): Promise<void> => {
  await api.delete(`/snippets/${slug}`);
};

export const listMySnippets = async (params?: {
  q?: string;
  visibility?: SnippetVisibility | '';
  sort?: string;
  order?: string;
}): Promise<SnippetList> => {
  const response = await api.get<SnippetList>('/me/snippets', { params });
  return response.data;
};

export const getAuthProviders = async (): Promise<AuthProviders> => {
  const response = await api.get<AuthProviders>('/auth/providers');
  return response.data;
};

export const getCurrentUser = async (): Promise<User | null> => {
  const response = await api.get<{ user: User | null }>('/auth/me');
  return response.data.user ?? null;
};

export const loginWithPassword = async (email: string, password: string): Promise<User> => {
  const response = await api.post<{ user: User }>('/auth/login', { email, password });
  return response.data.user;
};

export const registerAccount = async (email: string, password: string, name: string): Promise<User> => {
  const response = await api.post<{ user: User }>('/auth/register', { email, password, name });
  return response.data.user;
};

export const logoutCurrentUser = async (): Promise<void> => {
  await api.post('/auth/logout');
};

export const githubLoginUrl = (next = '/'): string => {
  const params = new URLSearchParams({ next });
  return `${API_BASE_URL}/auth/github?${params.toString()}`;
};

export const listProblems = async (): Promise<ProblemSummary[]> => {
  const response = await api.get<ProblemSummary[]>('/problems');
  return response.data;
};

export const getProblem = async (slug: string): Promise<ProblemDetail> => {
  const response = await api.get<ProblemDetail>(`/problems/${slug}`);
  return response.data;
};

export const runSamples = async (
  slug: string,
  language: string,
  code: string,
  signal?: AbortSignal,
  onQueue?: (queue: QueueStatus | null) => void,
): Promise<JudgeResult> => {
  try {
    const response = await api.post<WorkTicket<JudgeResult>>(
      `/problems/${slug}/run-samples`,
      { language, code },
      { signal },
    );
    return await waitForJob(response.data, signal, onQueue);
  } catch (error) {
    throw rateLimitFromAxios(error) ?? error;
  }
};

export const submitSolution = async (
  slug: string,
  language: string,
  code: string,
  signal?: AbortSignal,
  onQueue?: (queue: QueueStatus | null) => void,
): Promise<JudgeResult> => {
  try {
    const response = await api.post<WorkTicket<JudgeResult>>(
      `/problems/${slug}/submissions`,
      { language, code },
      { signal },
    );
    return await waitForJob(response.data, signal, onQueue);
  } catch (error) {
    throw rateLimitFromAxios(error) ?? error;
  }
};

async function waitForJob<T>(
  ticket: WorkTicket<T>,
  signal?: AbortSignal,
  onQueue?: (queue: QueueStatus | null) => void,
): Promise<T> {
  let current = ticket;
  while (true) {
    if (signal?.aborted) {
      await api.delete(`/jobs/${current.id}`).catch(() => undefined);
      const cancelled = new Error('Canceled');
      cancelled.name = 'CanceledError';
      throw cancelled;
    }
    if (current.state === 'QUEUED') {
      onQueue?.({
        position: current.position || 1,
        estimatedWaitMs: current.estimatedWaitMs || 0,
      });
    } else {
      onQueue?.(null);
    }
    if (current.state === 'COMPLETED' && current.result) {
      return current.result;
    }
    if (current.state === 'COMPLETED' && current.error) {
      throw new Error(current.error);
    }
    if (current.state === 'REJECTED' || current.state === 'QUEUE_TIMEOUT') {
      throw new RateLimitedError(
        current.error || 'The execution queue is full. Try again shortly.',
        current.retryAfterSeconds || 1,
        current.reason || current.state,
      );
    }
    if (current.state === 'CANCELLED') {
      const cancelled = new Error('Canceled');
      cancelled.name = 'CanceledError';
      throw cancelled;
    }
    await sleep(300, signal);
    try {
      current = (await api.get<WorkTicket<T>>(`/jobs/${ticket.id}`, { signal })).data;
    } catch (error) {
      throw rateLimitFromAxios(error) ?? error;
    }
  }
}

function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      const cancelled = new Error('Canceled');
      cancelled.name = 'CanceledError';
      reject(cancelled);
      return;
    }
    const timer = window.setTimeout(resolve, ms);
    signal?.addEventListener('abort', () => {
      window.clearTimeout(timer);
      const cancelled = new Error('Canceled');
      cancelled.name = 'CanceledError';
      reject(cancelled);
    }, { once: true });
  });
}

export const listSubmissions = async (slug: string): Promise<SubmissionSummary[]> => {
  const response = await api.get<SubmissionSummary[]>(`/problems/${slug}/submissions`);
  return response.data;
};

export const getSubmission = async (id: number): Promise<JudgeResult> => {
  const response = await api.get<JudgeResult>(`/submissions/${id}`);
  return response.data;
};
