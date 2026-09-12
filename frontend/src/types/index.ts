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

export interface Snippet {
  slug: string;
  language: string;
  code: string;
  stdin: string;
  title: string | null;
  createdAt: string;
  updatedAt: string;
  viewCount: number;
  forkedFrom: string | null;
}

export interface SnippetRequest {
  language: string;
  code: string;
  stdin?: string;
  title?: string;
}

export type ExecutionStatus =
  | 'SUCCESS'
  | 'COMPILE_ERROR'
  | 'RUNTIME_ERROR'
  | 'TIMEOUT'
  | 'MEMORY_EXCEEDED'
  | 'STOPPED'
  | 'ERROR';

export interface ExecutionResponse {
  output: string;
  error: string;
  executionTime: number;
  status: ExecutionStatus;
}

export interface RunHistoryEntry {
  id: string;
  createdAt: number;
  language: string;
  code: string;
  stdin: string;
  status: ExecutionStatus;
  executionTime: number;
  outputPreview: string;
  errorPreview: string;
  outputTruncated: boolean;
}

export type EditorTheme = 'vs-dark' | 'light';

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';

export type Verdict =
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'RUNTIME_ERROR'
  | 'COMPILE_ERROR'
  | 'ERROR';

export interface ProblemSummary {
  slug: string;
  title: string;
  difficulty: Difficulty;
  tags: string[];
  timeLimitMs: number;
  memoryLimitBytes: number;
  sampleCount: number;
  totalTestCases: number;
}

export interface SampleCase {
  index: number;
  input: string;
  expectedOutput: string;
  points: number;
}

export interface ProblemDetail {
  slug: string;
  title: string;
  description: string;
  difficulty: Difficulty;
  tags: string[];
  timeLimitMs: number;
  memoryLimitBytes: number;
  samples: SampleCase[];
  hiddenTestCount: number;
}

export interface JudgeCaseResult {
  index: number;
  sample: boolean;
  verdict: Verdict;
  runtimeMs: number;
  points: number;
  input?: string | null;
  expectedOutput?: string | null;
  actualOutput?: string | null;
  error?: string | null;
}

export interface JudgeResult {
  id?: number | null;
  problemSlug: string;
  language: string;
  code: string;
  verdict: Verdict;
  runtimeMs: number;
  passedCount: number;
  totalCount: number;
  score: number;
  maxScore: number;
  compileError?: string | null;
  createdAt?: string | null;
  cases: JudgeCaseResult[];
}

export interface SubmissionSummary {
  id: number;
  problemSlug: string;
  language: string;
  verdict: Verdict;
  runtimeMs: number;
  passedCount: number;
  totalCount: number;
  score: number;
  maxScore: number;
  createdAt: string;
}
