import axios from 'axios';
import { ExecutionRequest, ExecutionResponse, Language } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const executeCode = async (request: ExecutionRequest): Promise<ExecutionResponse> => {
  const response = await api.post<ExecutionResponse>('/execute', request);
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
