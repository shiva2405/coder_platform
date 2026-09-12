import { useCallback, useEffect, useState } from 'react';
import { RunHistoryEntry } from '../types';
import { addRunHistory, clearRunHistory, listRunHistory, NewRunHistoryInput } from '../services/runHistoryStorage';

export function useRunHistory() {
  const [runs, setRuns] = useState<RunHistoryEntry[]>([]);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setRuns(await listRunHistory());
      setError(null);
    } catch (err) {
      console.error('Failed to load run history:', err);
      setError('Run history is unavailable in this browser.');
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const recordRun = useCallback(async (input: NewRunHistoryInput) => {
    try {
      await addRunHistory(input);
      await refresh();
    } catch (err) {
      console.error('Failed to save run history:', err);
    }
  }, [refresh]);

  const clearHistory = useCallback(async () => {
    await clearRunHistory();
    setRuns([]);
  }, []);

  return { runs, error, recordRun, clearHistory };
}
