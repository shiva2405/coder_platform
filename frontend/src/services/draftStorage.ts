import { ProjectFile } from '../types';
import { snapshotFromSnippet } from './projectFiles';

const HOME_KEY = 'coder-platform:unsaved-draft';
const SLUG_KEY_PREFIX = 'coder-platform:unsaved-draft:';

export interface EditorDraft {
  languageId: string;
  code: string;
  files?: ProjectFile[];
  entrypoint?: string;
  openTabs?: string[];
  activePath?: string;
  stdin: string;
  updatedAt: number;
}

function storageKey(slug: string | null): string {
  return slug ? `${SLUG_KEY_PREFIX}${slug}` : HOME_KEY;
}

export function loadDraft(slug: string | null): EditorDraft | null {
  try {
    const raw = localStorage.getItem(storageKey(slug));
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<EditorDraft>;
    if (typeof parsed.languageId !== 'string' || typeof parsed.code !== 'string') {
      return null;
    }
    const files = Array.isArray(parsed.files)
      ? parsed.files.filter((file): file is ProjectFile =>
          Boolean(file && typeof file.path === 'string' && typeof file.content === 'string'))
      : undefined;
    const snapshot = snapshotFromSnippet(parsed.languageId, parsed.code, files, parsed.entrypoint);
    return {
      languageId: snapshot.languageId,
      code: snapshot.files.find((file) => file.path === snapshot.entrypoint)?.content ?? parsed.code,
      files: snapshot.files,
      entrypoint: snapshot.entrypoint,
      openTabs: Array.isArray(parsed.openTabs) ? parsed.openTabs.filter((path) => typeof path === 'string') : undefined,
      activePath: typeof parsed.activePath === 'string' ? parsed.activePath : undefined,
      stdin: typeof parsed.stdin === 'string' ? parsed.stdin : '',
      updatedAt: typeof parsed.updatedAt === 'number' ? parsed.updatedAt : 0,
    };
  } catch {
    return null;
  }
}

export function saveDraft(slug: string | null, draft: Omit<EditorDraft, 'updatedAt'>): void {
  try {
    const payload: EditorDraft = {
      ...draft,
      updatedAt: Date.now(),
    };
    localStorage.setItem(storageKey(slug), JSON.stringify(payload));
  } catch {
    // Ignore quota / private-mode failures
  }
}

export function draftsDiffer(
  draft: EditorDraft,
  snippet: { language: string; code: string; stdin: string; files?: ProjectFile[]; entrypoint?: string }
): boolean {
  const draftFiles = draft.files ?? [{ path: draft.entrypoint || 'main', content: draft.code }];
  const snippetProject = snapshotFromSnippet(snippet.language, snippet.code, snippet.files, snippet.entrypoint);
  if (draft.languageId !== snippet.language || draft.stdin !== snippet.stdin) {
    return true;
  }
  if ((draft.entrypoint || snippetProject.entrypoint) !== snippetProject.entrypoint) {
    return true;
  }
  if (draftFiles.length !== snippetProject.files.length) {
    return true;
  }
  const byPath = new Map(snippetProject.files.map((file) => [file.path, file.content]));
  return draftFiles.some((file) => byPath.get(file.path) !== file.content);
}
