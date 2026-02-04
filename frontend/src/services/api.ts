import axios, { AxiosInstance } from 'axios';
import { ExecutionRequest, ExecutionResponse, Language } from '../types';

// Support runtime configuration for Azure deployment
declare global {
  interface Window {
    SIMPLYCODE_CONFIG?: {
      apiUrl?: string;
    };
  }
}

// Priority: 1. Runtime config (Azure), 2. Build-time env var, 3. Default to /api
const getApiBaseUrl = (): string => {
  if (typeof window !== 'undefined' && window.SIMPLYCODE_CONFIG?.apiUrl) {
    return window.SIMPLYCODE_CONFIG.apiUrl;
  }
  return import.meta.env.VITE_API_URL || '/api';
};

// Lazy initialization of axios instance to ensure config.js has loaded
let _api: AxiosInstance | null = null;

const getApi = (): AxiosInstance => {
  if (!_api) {
    const baseURL = getApiBaseUrl();
    console.log('Initializing API with baseURL:', baseURL);
    _api = axios.create({
      baseURL,
      headers: {
        'Content-Type': 'application/json',
      },
      timeout: 60000, // 60 second timeout for code execution
    });
  }
  return _api;
};

export const executeCode = async (request: ExecutionRequest): Promise<ExecutionResponse> => {
  const response = await getApi().post<ExecutionResponse>('/execute', request);
  return response.data;
};

export const getLanguages = async (): Promise<Language[]> => {
  const response = await getApi().get<Language[]>('/languages');
  return response.data;
};

export const checkHealth = async (): Promise<boolean> => {
  try {
    await getApi().get('/health');
    return true;
  } catch {
    return false;
  }
};
