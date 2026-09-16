import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import OutputPanel from './OutputPanel';
import { ExecutionResponse } from '../types';

const baseProps = {
  isLoading: false,
  live: false,
  liveOutput: '',
  liveError: '',
  stdin: '',
  onStdinChange: () => undefined,
  interactive: false,
};

function result(status: ExecutionResponse['status'], extra: Partial<ExecutionResponse> = {}): ExecutionResponse {
  return {
    output: extra.output ?? 'hello',
    error: extra.error ?? '',
    executionTime: extra.executionTime ?? 12,
    status,
  };
}

describe('OutputPanel', () => {
  it('shows an empty prompt before any run', () => {
    render(<OutputPanel {...baseProps} result={null} />);
    expect(screen.getByText(/run your code to see the output/i)).toBeInTheDocument();
  });

  it('renders successful output and execution time', () => {
    render(<OutputPanel {...baseProps} result={result('SUCCESS')} />);
    expect(screen.getByText('Execution Successful')).toBeInTheDocument();
    expect(screen.getByText('hello')).toBeInTheDocument();
    expect(screen.getByText(/execution time: 12ms/i)).toBeInTheDocument();
  });

  it.each([
    ['COMPILE_ERROR', 'Compilation Error'],
    ['RUNTIME_ERROR', 'Runtime Error'],
    ['TIMEOUT', 'Time Limit Exceeded'],
    ['MEMORY_EXCEEDED', 'Memory Limit Exceeded'],
    ['STOPPED', 'Stopped'],
    ['ERROR', 'Error'],
  ] as const)('maps %s to %s', (status, label) => {
    render(
      <OutputPanel
        {...baseProps}
        result={result(status, { error: 'boom', output: '' })}
      />,
    );
    expect(screen.getAllByText(label).length).toBeGreaterThan(0);
    expect(screen.getByText('boom')).toBeInTheDocument();
  });

  it('shows queue position while waiting', () => {
    render(
      <OutputPanel
        {...baseProps}
        result={null}
        isLoading
        queue={{ position: 3, estimatedWaitMs: 4000 }}
      />,
    );
    expect(screen.getByText(/queued — position 3/i)).toBeInTheDocument();
    expect(screen.getByText(/waiting for an execution slot/i)).toBeInTheDocument();
  });

  it('sends interactive input', async () => {
    const user = userEvent.setup();
    const onSendInput = vi.fn();

    render(
      <OutputPanel
        {...baseProps}
        result={null}
        isLoading
        live
        interactive
        onSendInput={onSendInput}
      />,
    );

    await user.type(screen.getByPlaceholderText(/type a line/i), 'alice');
    await user.click(screen.getByRole('button', { name: /send/i }));
    expect(onSendInput).toHaveBeenCalledWith('alice\n');
  });
});
