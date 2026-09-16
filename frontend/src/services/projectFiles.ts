import { ProjectFile } from '../types';

export const MAX_PROJECT_FILES = 32;
export const MAX_FILE_BYTES = 256 * 1024;
export const MAX_PROJECT_BYTES = 512 * 1024;
export const MAX_PATH_LENGTH = 180;
export const MAX_PATH_DEPTH = 8;

const SEGMENT = /^[A-Za-z0-9._][A-Za-z0-9._-]*$/;

const DEFAULT_FILES: Record<string, string> = {
  java: 'Main.java',
  python: 'main.py',
  javascript: 'main.js',
  typescript: 'main.ts',
  c: 'main.c',
  cpp: 'main.cpp',
  go: 'main.go',
  rust: 'main.rs',
  ruby: 'main.rb',
  php: 'main.php',
  kotlin: 'Main.kt',
  swift: 'main.swift',
  perl: 'main.pl',
  bash: 'main.sh',
};

const EXT_LANGUAGE: Record<string, string> = {
  '.java': 'java',
  '.py': 'python',
  '.js': 'javascript',
  '.mjs': 'javascript',
  '.cjs': 'javascript',
  '.ts': 'typescript',
  '.tsx': 'typescript',
  '.c': 'c',
  '.h': 'c',
  '.cpp': 'cpp',
  '.cc': 'cpp',
  '.cxx': 'cpp',
  '.hpp': 'cpp',
  '.hh': 'cpp',
  '.go': 'go',
  '.rs': 'rust',
  '.rb': 'ruby',
  '.php': 'php',
  '.kt': 'kotlin',
  '.kts': 'kotlin',
  '.swift': 'swift',
  '.pl': 'perl',
  '.pm': 'perl',
  '.sh': 'shell',
  '.bash': 'shell',
  '.json': 'json',
  '.md': 'markdown',
  '.txt': 'plaintext',
  '.mod': 'go',
};

export interface ProjectSnapshot {
  languageId: string;
  files: ProjectFile[];
  entrypoint: string;
}

export function defaultFileName(languageId: string): string {
  return DEFAULT_FILES[languageId] || 'main.txt';
}

export function defaultProject(languageId: string, sampleCode: string): ProjectSnapshot {
  const path = defaultFileName(languageId);
  return {
    languageId,
    files: [{ path, content: sampleCode }],
    entrypoint: path,
  };
}

export function projectFromLegacy(languageId: string, code: string): ProjectSnapshot {
  return defaultProject(languageId, code);
}

export function normalizeProjectPath(rawPath: string): string {
  if (!rawPath || !rawPath.trim()) {
    throw new Error('File path is required');
  }
  let path = rawPath.trim().replace(/\\/g, '/');
  if (/[\0\u0001-\u001F]/.test(path)) {
    throw new Error('File path contains invalid characters');
  }
  if (path.startsWith('/') || path.startsWith('~') || /^[A-Za-z]:/.test(path)) {
    throw new Error('Absolute file paths are not allowed');
  }
  while (path.startsWith('./')) {
    path = path.slice(2);
  }
  if (path.endsWith('/')) {
    throw new Error('File path must name a file, not a directory');
  }
  const segments: string[] = [];
  for (const part of path.split('/')) {
    if (!part || part === '.') {
      continue;
    }
    if (part === '..') {
      throw new Error("File path cannot contain '..'");
    }
    if (!SEGMENT.test(part)) {
      throw new Error(`File path contains invalid characters: ${rawPath}`);
    }
    segments.push(part);
  }
  if (segments.length === 0) {
    throw new Error('File path is required');
  }
  if (segments.length > MAX_PATH_DEPTH) {
    throw new Error('File path is too deep');
  }
  const normalized = segments.join('/');
  if (normalized.length > MAX_PATH_LENGTH) {
    throw new Error(`File path exceeds ${MAX_PATH_LENGTH} characters`);
  }
  return normalized;
}

export function fileName(path: string): string {
  const slash = path.lastIndexOf('/');
  return slash < 0 ? path : path.slice(slash + 1);
}

export function parentDir(path: string): string {
  const slash = path.lastIndexOf('/');
  return slash < 0 ? '' : path.slice(0, slash);
}

export function joinPath(dir: string, name: string): string {
  return dir ? `${dir.replace(/\/$/, '')}/${name}` : name;
}

export function uniquePath(desired: string, existing: string[]): string {
  const used = new Set(existing);
  if (!used.has(desired)) {
    return desired;
  }
  const slash = desired.lastIndexOf('/');
  const dir = slash < 0 ? '' : desired.slice(0, slash + 1);
  const base = slash < 0 ? desired : desired.slice(slash + 1);
  const dot = base.lastIndexOf('.');
  const stem = dot > 0 ? base.slice(0, dot) : base;
  const ext = dot > 0 ? base.slice(dot) : '';
  let index = 2;
  let candidate = `${dir}${stem}${index}${ext}`;
  while (used.has(candidate)) {
    index += 1;
    candidate = `${dir}${stem}${index}${ext}`;
  }
  return candidate;
}

export function entrypointContent(files: ProjectFile[], entrypoint: string): string {
  return files.find((file) => file.path === entrypoint)?.content ?? '';
}

export function replaceFile(files: ProjectFile[], path: string, content: string): ProjectFile[] {
  return files.map((file) => (file.path === path ? { ...file, content } : file));
}

export function sortFiles(files: ProjectFile[]): ProjectFile[] {
  return [...files].sort((a, b) => a.path.localeCompare(b.path));
}

export function projectByteLength(files: ProjectFile[]): number {
  const encoder = new TextEncoder();
  return files.reduce((sum, file) => sum + encoder.encode(file.content || '').length, 0);
}

export function validateProject(files: ProjectFile[], entrypoint: string): string | null {
  if (files.length === 0) {
    return 'A project needs at least one file.';
  }
  if (files.length > MAX_PROJECT_FILES) {
    return `Projects are limited to ${MAX_PROJECT_FILES} files.`;
  }
  const encoder = new TextEncoder();
  const seen = new Set<string>();
  let total = 0;
  for (const file of files) {
    try {
      const path = normalizeProjectPath(file.path);
      if (seen.has(path)) {
        return `Duplicate file path: ${path}`;
      }
      seen.add(path);
    } catch (error) {
      return error instanceof Error ? error.message : 'Invalid file path';
    }
    const size = encoder.encode(file.content || '').length;
    if (size > MAX_FILE_BYTES) {
      return `${file.path} exceeds the 256KB limit.`;
    }
    total += size;
  }
  if (total > MAX_PROJECT_BYTES) {
    return 'Project exceeds the 512KB limit.';
  }
  try {
    const entry = normalizeProjectPath(entrypoint);
    if (!seen.has(entry)) {
      return `Entrypoint does not exist: ${entry}`;
    }
  } catch (error) {
    return error instanceof Error ? error.message : 'Invalid entrypoint';
  }
  return null;
}

export function monacoLanguageFromPath(path: string, fallbackLanguageId = 'plaintext'): string {
  const name = fileName(path);
  const dot = name.lastIndexOf('.');
  if (dot < 0) {
    return fallbackLanguageId;
  }
  return EXT_LANGUAGE[name.slice(dot).toLowerCase()] || fallbackLanguageId;
}

export function projectsEqual(
  left: { languageId: string; files: ProjectFile[]; entrypoint: string; stdin: string },
  right: { languageId: string; files: ProjectFile[]; entrypoint: string; stdin: string },
): boolean {
  if (left.languageId !== right.languageId || left.entrypoint !== right.entrypoint || left.stdin !== right.stdin) {
    return false;
  }
  if (left.files.length !== right.files.length) {
    return false;
  }
  const rightByPath = new Map(right.files.map((file) => [file.path, file.content]));
  return left.files.every((file) => rightByPath.get(file.path) === file.content);
}

export interface FileTreeNode {
  name: string;
  path: string;
  type: 'file' | 'folder';
  children?: FileTreeNode[];
}

export function buildFileTree(paths: string[], extraFolders: string[] = []): FileTreeNode[] {
  const root: FileTreeNode[] = [];

  const ensureFolder = (segments: string[]): FileTreeNode[] => {
    let level = root;
    let current = '';
    for (const segment of segments) {
      current = current ? `${current}/${segment}` : segment;
      let folder = level.find((node) => node.type === 'folder' && node.name === segment);
      if (!folder) {
        folder = { name: segment, path: current, type: 'folder', children: [] };
        level.push(folder);
      }
      level = folder.children!;
    }
    return level;
  };

  for (const folder of extraFolders) {
    if (folder) {
      ensureFolder(folder.split('/').filter(Boolean));
    }
  }

  for (const path of [...paths].sort((a, b) => a.localeCompare(b))) {
    const parts = path.split('/').filter(Boolean);
    const name = parts.pop();
    if (!name) {
      continue;
    }
    const parent = ensureFolder(parts);
    if (!parent.some((node) => node.type === 'file' && node.path === path)) {
      parent.push({ name, path, type: 'file' });
    }
  }

  const sortLevel = (nodes: FileTreeNode[]) => {
    nodes.sort((a, b) => {
      if (a.type !== b.type) {
        return a.type === 'folder' ? -1 : 1;
      }
      return a.name.localeCompare(b.name);
    });
    nodes.forEach((node) => {
      if (node.children) {
        sortLevel(node.children);
      }
    });
  };
  sortLevel(root);
  return root;
}

export function snapshotFromSnippet(
  languageId: string,
  code: string,
  files?: ProjectFile[] | null,
  entrypoint?: string | null,
): ProjectSnapshot {
  if (files && files.length > 0) {
    const normalized = files.map((file) => ({
      path: normalizeProjectPath(file.path),
      content: file.content ?? '',
    }));
    const entry = entrypoint && normalized.some((file) => file.path === entrypoint)
      ? entrypoint
      : normalized[0].path;
    return { languageId, files: sortFiles(normalized), entrypoint: entry };
  }
  return projectFromLegacy(languageId, code);
}
