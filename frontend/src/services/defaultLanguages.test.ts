import { describe, expect, it } from 'vitest';
import { fallbackLanguages, loadLanguages } from './defaultLanguages';

describe('language catalog fallback', () => {
  it('includes every supported playground language', () => {
    const languages = fallbackLanguages();
    expect(languages.map((language) => language.id)).toEqual([
      'java',
      'python',
      'javascript',
      'typescript',
      'c',
      'cpp',
      'go',
      'rust',
      'ruby',
      'php',
      'kotlin',
      'swift',
      'perl',
      'bash',
    ]);
    expect(languages.every((language) => language.sampleCode.length > 0)).toBe(true);
  });

  it('uses the API catalog when it is available', async () => {
    const apiLanguages = [
      { id: 'python', name: 'Python', extension: '.py', sampleCode: 'print(1)' },
    ];

    const result = await loadLanguages(async () => apiLanguages);

    expect(result.offline).toBe(false);
    expect(result.languages).toEqual(apiLanguages);
  });

  it('falls back when the language API throws', async () => {
    const result = await loadLanguages(async () => {
      throw new Error('network down');
    });

    expect(result.offline).toBe(true);
    expect(result.languages.map((language) => language.id)).toContain('python');
    expect(result.languages).toHaveLength(fallbackLanguages().length);
  });

  it('falls back when the language API returns an empty list', async () => {
    const result = await loadLanguages(async () => []);

    expect(result.offline).toBe(true);
    expect(result.languages.length).toBeGreaterThan(0);
  });
});

describe('monaco language mapping used by the editor', () => {
  it('is covered alongside the catalog fallback', async () => {
    const { getMonacoLanguage } = await import('./monacoLanguage');
    expect(getMonacoLanguage('bash')).toBe('shell');
    expect(getMonacoLanguage('unknown')).toBe('plaintext');
  });
});
