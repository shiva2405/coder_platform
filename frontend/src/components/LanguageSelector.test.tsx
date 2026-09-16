import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import LanguageSelector from './LanguageSelector';
import { Language } from '../types';

const languages: Language[] = [
  { id: 'python', name: 'Python', extension: '.py', sampleCode: 'print(1)' },
  { id: 'java', name: 'Java', extension: '.java', sampleCode: 'class Main {}' },
];

describe('LanguageSelector', () => {
  it('shows the selected language and notifies on change', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();

    render(
      <LanguageSelector
        languages={languages}
        selectedLanguage={languages[0]}
        onSelect={onSelect}
      />,
    );

    expect(screen.getByRole('button', { name: /python/i })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /python/i }));
    await user.click(screen.getByRole('button', { name: /java \.java/i }));

    expect(onSelect).toHaveBeenCalledWith(languages[1]);
  });

  it('prompts the user to select a language when none is chosen', () => {
    render(
      <LanguageSelector languages={languages} selectedLanguage={null} onSelect={() => undefined} />,
    );

    expect(screen.getByRole('button', { name: /select language/i })).toBeInTheDocument();
  });
});
