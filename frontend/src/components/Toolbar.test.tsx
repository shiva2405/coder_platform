import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import Toolbar from './Toolbar';
import { Language } from '../types';

vi.mock('../auth/AuthProvider', () => ({
  useAuth: () => ({ user: null, loading: false, logout: () => undefined }),
}));

const python: Language = {
  id: 'python',
  name: 'Python',
  extension: '.py',
  sampleCode: 'print(1)',
};

const defaults = {
  languages: [python],
  selectedLanguage: python,
  onLanguageSelect: () => undefined,
  onReset: () => undefined,
  onShare: () => undefined,
  isSharing: false,
  shareCopied: false,
  canShare: true,
  shareLabel: 'Share',
  theme: 'vs-dark' as const,
  onThemeToggle: () => undefined,
  historyOpen: false,
  onToggleHistory: () => undefined,
};

describe('Toolbar', () => {
  it('runs the current program', async () => {
    const user = userEvent.setup();
    const onRun = vi.fn();

    render(
      <MemoryRouter>
        <Toolbar {...defaults} onRun={onRun} onStop={() => undefined} isRunning={false} />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button', { name: /run/i }));
    expect(onRun).toHaveBeenCalled();
  });

  it('shows stop while a program is running', async () => {
    const user = userEvent.setup();
    const onStop = vi.fn();

    render(
      <MemoryRouter>
        <Toolbar {...defaults} onRun={() => undefined} onStop={onStop} isRunning />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button', { name: /stop/i }));
    expect(onStop).toHaveBeenCalled();
  });

  it('disables run when no language is selected', () => {
    render(
      <MemoryRouter>
        <Toolbar
          {...defaults}
          selectedLanguage={null}
          onRun={() => undefined}
          onStop={() => undefined}
          isRunning={false}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole('button', { name: /run/i })).toBeDisabled();
  });
});
