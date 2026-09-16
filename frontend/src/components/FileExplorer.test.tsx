import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import FileExplorer from './FileExplorer';

describe('FileExplorer', () => {
  it('opens a file and can mark the entrypoint', async () => {
    const user = userEvent.setup();
    const onOpen = vi.fn();
    const onSetEntrypoint = vi.fn();

    render(
      <FileExplorer
        files={[
          { path: 'Main.java', content: '' },
          { path: 'src/Util.java', content: '' },
        ]}
        activePath="Main.java"
        entrypoint="Main.java"
        onOpen={onOpen}
        onNewFile={() => undefined}
        onNewFolder={() => undefined}
        onRename={() => undefined}
        onDelete={() => undefined}
        onSetEntrypoint={onSetEntrypoint}
      />,
    );

    expect(screen.getByText('Main.java')).toBeInTheDocument();
    await user.click(screen.getByText('Util.java'));
    expect(onOpen).toHaveBeenCalledWith('src/Util.java');
  });
});
