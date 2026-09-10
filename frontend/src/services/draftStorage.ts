const HOME_KEY = 'coder-platform:unsaved-draft';
const SLUG_KEY_PREFIX = 'coder-platform:unsaved-draft:';

export interface EditorDraft {
  languageId: string;
  code: string;
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
    return {
      languageId: parsed.languageId,
      code: parsed.code,
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
  snippet: { language: string; code: string; stdin: string }
): boolean {
  return (
    draft.languageId !== snippet.language ||
    draft.code !== snippet.code ||
    draft.stdin !== snippet.stdin
  );
}
