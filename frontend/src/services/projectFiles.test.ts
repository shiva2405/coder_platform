import { describe, expect, it } from 'vitest';
import {
  buildFileTree,
  monacoLanguageFromPath,
  normalizeProjectPath,
  snapshotFromSnippet,
  uniquePath,
  validateProject,
} from './projectFiles';

describe('projectFiles', () => {
  it('normalizes relative paths and rejects traversal', () => {
    expect(normalizeProjectPath('./src/Util.java')).toBe('src/Util.java');
    expect(normalizeProjectPath('src\\\\Util.java')).toBe('src/Util.java');
    expect(() => normalizeProjectPath('../secret.py')).toThrow("File path cannot contain '..'");
    expect(() => normalizeProjectPath('/etc/passwd')).toThrow('Absolute file paths are not allowed');
    expect(() => normalizeProjectPath('C:/Windows/system.ini')).toThrow('Absolute file paths are not allowed');
  });

  it('validates project size and entrypoint', () => {
    expect(validateProject([{ path: 'main.py', content: 'print(1)' }], 'main.py')).toBeNull();
    expect(validateProject([{ path: 'main.py', content: 'print(1)' }], 'other.py')).toContain('Entrypoint does not exist');
  });

  it('builds a folder tree', () => {
    const tree = buildFileTree(['src/Main.java', 'src/util/Helper.java', 'README.md'], ['docs']);
    expect(tree.map((node) => node.name)).toEqual(['docs', 'src', 'README.md']);
    const src = tree.find((node) => node.name === 'src');
    expect(src?.children?.map((node) => node.name)).toEqual(['util', 'Main.java']);
  });

  it('detects editor language from the file extension', () => {
    expect(monacoLanguageFromPath('util.h', 'cpp')).toBe('c');
    expect(monacoLanguageFromPath('main.rs', 'python')).toBe('rust');
    expect(monacoLanguageFromPath('notes', 'python')).toBe('python');
  });

  it('loads a legacy single-file snippet as a project', () => {
    const project = snapshotFromSnippet('python', 'print(1)');
    expect(project.entrypoint).toBe('main.py');
    expect(project.files).toEqual([{ path: 'main.py', content: 'print(1)' }]);
  });

  it('allocates a unique path', () => {
    expect(uniquePath('main.py', ['main.py'])).toBe('main2.py');
  });
});
