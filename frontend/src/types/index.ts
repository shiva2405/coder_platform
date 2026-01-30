export interface Language {
  id: string;
  name: string;
  extension: string;
  sampleCode: string;
}

export interface ExecutionRequest {
  language: string;
  code: string;
  stdin?: string;
}

export interface ExecutionResponse {
  output: string;
  error: string;
  executionTime: number;
  status: 'SUCCESS' | 'COMPILE_ERROR' | 'RUNTIME_ERROR' | 'TIMEOUT' | 'MEMORY_EXCEEDED' | 'ERROR';
}

export type EditorTheme = 'vs-dark' | 'light';
