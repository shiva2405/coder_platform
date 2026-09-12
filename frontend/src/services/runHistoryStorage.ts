import { ExecutionResponse, RunHistoryEntry } from '../types';

export const MAX_RUN_HISTORY = 50;
export const OUTPUT_PREVIEW_LIMIT = 500;

const DB_NAME = 'coder-platform';
const DB_VERSION = 1;
const STORE_NAME = 'run-history';

export interface NewRunHistoryInput {
  language: string;
  code: string;
  stdin: string;
  status: RunHistoryEntry['status'];
  executionTime: number;
  output: string;
  error: string;
}

export function truncatePreview(value: string, limit = OUTPUT_PREVIEW_LIMIT): { text: string; truncated: boolean } {
  if (!value) {
    return { text: '', truncated: false };
  }
  if (value.length <= limit) {
    return { text: value, truncated: false };
  }
  return { text: value.slice(0, limit), truncated: true };
}

export function historyToResult(entry: RunHistoryEntry): ExecutionResponse {
  return {
    output: entry.outputPreview,
    error: entry.errorPreview,
    executionTime: entry.executionTime,
    status: entry.status,
  };
}

function newId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function requestToPromise<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function waitForTransaction(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' });
        store.createIndex('createdAt', 'createdAt');
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export function buildHistoryEntry(input: NewRunHistoryInput, createdAt = Date.now()): RunHistoryEntry {
  const output = truncatePreview(input.output);
  const error = truncatePreview(input.error);
  return {
    id: newId(),
    createdAt,
    language: input.language,
    code: input.code,
    stdin: input.stdin,
    status: input.status,
    executionTime: input.executionTime,
    outputPreview: output.text,
    errorPreview: error.text,
    outputTruncated: output.truncated,
  };
}

export async function listRunHistory(): Promise<RunHistoryEntry[]> {
  const db = await openDb();
  try {
    const tx = db.transaction(STORE_NAME, 'readonly');
    const store = tx.objectStore(STORE_NAME);
    const index = store.index('createdAt');
    const rows = await requestToPromise(index.getAll());
    return (rows as RunHistoryEntry[]).slice().sort((a, b) => b.createdAt - a.createdAt);
  } finally {
    db.close();
  }
}

export async function addRunHistory(input: NewRunHistoryInput): Promise<RunHistoryEntry> {
  const entry = buildHistoryEntry(input);
  const db = await openDb();
  try {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    store.put(entry);

    const index = store.index('createdAt');
    const rows = await requestToPromise(index.getAll());
    const sorted = (rows as RunHistoryEntry[]).slice().sort((a, b) => a.createdAt - b.createdAt);
    const extras = sorted.slice(0, Math.max(0, sorted.length - MAX_RUN_HISTORY));
    extras.forEach((row) => store.delete(row.id));

    await waitForTransaction(tx);
    return entry;
  } finally {
    db.close();
  }
}

export async function clearRunHistory(): Promise<void> {
  const db = await openDb();
  try {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).clear();
    await waitForTransaction(tx);
  } finally {
    db.close();
  }
}
