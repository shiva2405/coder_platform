export class RateLimitedError extends Error {
  retryAfterSeconds: number;
  reason: string;

  constructor(message: string, retryAfterSeconds: number, reason = 'RATE_LIMIT') {
    super(message);
    this.name = 'RateLimitedError';
    this.retryAfterSeconds = Math.max(1, Math.round(retryAfterSeconds || 1));
    this.reason = reason;
  }
}

export function retryAfterFromAxios(error: any): number {
  const header = error?.response?.headers?.['retry-after'] ?? error?.response?.headers?.['Retry-After'];
  const fromHeader = Number(header);
  const fromBody = Number(error?.response?.data?.retryAfterSeconds);
  if (Number.isFinite(fromHeader) && fromHeader > 0) {
    return fromHeader;
  }
  if (Number.isFinite(fromBody) && fromBody > 0) {
    return fromBody;
  }
  return 1;
}

export function rateLimitFromAxios(error: any): RateLimitedError | null {
  if (error instanceof RateLimitedError) {
    return error;
  }
  if (error?.response?.status !== 429) {
    return null;
  }
  const message = error.response?.data?.error || error.message || 'Too many requests. Try again shortly.';
  const reason = error.response?.data?.reason || 'RATE_LIMIT';
  return new RateLimitedError(message, retryAfterFromAxios(error), reason);
}

export function formatWait(ms: number): string {
  const seconds = Math.max(1, Math.round(ms / 1000));
  return seconds === 1 ? '1 second' : `${seconds} seconds`;
}
