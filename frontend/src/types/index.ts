export interface Language {
  id: string;
  name: string;
  extension: string;
  sampleCode: string;
}

export interface ProjectFile {
  path: string;
  content: string;
}

export interface ExecutionRequest {
  language: string;
  code: string;
  stdin?: string;
  files?: ProjectFile[];
  entrypoint?: string;
}

export type SnippetVisibility = 'PUBLIC' | 'UNLISTED' | 'PRIVATE';

export type UserRole = 'USER' | 'ADMIN';

export interface User {
  id: number;
  email: string;
  name: string;
  avatarUrl: string | null;
  role: UserRole;
  createdAt: string;
  updatedAt: string;
  lastLoginAt?: string | null;
}

export interface UserSummary {
  id: number;
  name: string;
  avatarUrl: string | null;
}

export interface Snippet {
  slug: string;
  language: string;
  code: string;
  files?: ProjectFile[];
  entrypoint?: string;
  stdin: string;
  title: string | null;
  createdAt: string;
  updatedAt: string;
  viewCount: number;
  forkedFrom: string | null;
  visibility: SnippetVisibility;
  owner: UserSummary | null;
  ownedByMe: boolean;
}

export interface SnippetSummary {
  slug: string;
  language: string;
  title: string | null;
  visibility: SnippetVisibility;
  createdAt: string;
  updatedAt: string;
  viewCount: number;
  forkedFrom: string | null;
}

export interface SnippetList {
  snippets: SnippetSummary[];
  total: number;
}

export interface SnippetRequest {
  language: string;
  code: string;
  files?: ProjectFile[];
  entrypoint?: string;
  stdin?: string;
  title?: string;
  visibility?: SnippetVisibility;
}

export interface SnippetUpdateRequest {
  language?: string;
  code?: string;
  files?: ProjectFile[];
  entrypoint?: string;
  stdin?: string;
  title?: string;
  visibility?: SnippetVisibility;
}

export interface AuthProviders {
  local: boolean;
  github: boolean;
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

export type WorkState =
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'QUEUE_TIMEOUT';

export interface QueueStatus {
  position: number;
  estimatedWaitMs: number;
}

export interface WorkTicket<T = unknown> {
  id: string;
  state: WorkState;
  position?: number | null;
  estimatedWaitMs?: number | null;
  retryAfterSeconds?: number | null;
  reason?: string | null;
  error?: string | null;
  result?: T | null;
}

export interface RunHistoryEntry {
  id: string;
  createdAt: number;
  language: string;
  code: string;
  files?: ProjectFile[];
  entrypoint?: string;
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
