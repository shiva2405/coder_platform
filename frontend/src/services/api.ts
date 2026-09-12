import axios from 'axios';
import {
  ExecutionRequest,
  ExecutionResponse,
  JudgeResult,
  Language,
  ProblemDetail,
  ProblemSummary,
  Snippet,
  SnippetRequest,
  SubmissionSummary,
} from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const executeCode = async (
  request: ExecutionRequest,
  signal?: AbortSignal,
): Promise<ExecutionResponse> => {
  const response = await api.post<ExecutionResponse>('/execute', request, { signal });
  return response.data;
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

export const listProblems = async (): Promise<ProblemSummary[]> => {
  const response = await api.get<ProblemSummary[]>('/problems');
  return response.data;
};

export const getProblem = async (slug: string): Promise<ProblemDetail> => {
  const response = await api.get<ProblemDetail>(`/problems/${slug}`);
  return response.data;
};

export const runSamples = async (slug: string, language: string, code: string): Promise<JudgeResult> => {
  const response = await api.post<JudgeResult>(`/problems/${slug}/run-samples`, { language, code });
  return response.data;
};

export const submitSolution = async (slug: string, language: string, code: string): Promise<JudgeResult> => {
  const response = await api.post<JudgeResult>(`/problems/${slug}/submissions`, { language, code });
  return response.data;
};

export const listSubmissions = async (slug: string): Promise<SubmissionSummary[]> => {
  const response = await api.get<SubmissionSummary[]>(`/problems/${slug}/submissions`);
  return response.data;
};

export const getSubmission = async (id: number): Promise<JudgeResult> => {
  const response = await api.get<JudgeResult>(`/submissions/${id}`);
  return response.data;
};
