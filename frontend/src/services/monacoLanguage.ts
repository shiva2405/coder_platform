const LANGUAGE_MAP: Record<string, string> = {
  java: 'java',
  python: 'python',
  javascript: 'javascript',
  typescript: 'typescript',
  c: 'c',
  cpp: 'cpp',
  go: 'go',
  rust: 'rust',
  ruby: 'ruby',
  php: 'php',
  kotlin: 'kotlin',
  swift: 'swift',
  perl: 'perl',
  bash: 'shell',
};

export function getMonacoLanguage(languageId: string): string {
  return LANGUAGE_MAP[languageId] || 'plaintext';
}
