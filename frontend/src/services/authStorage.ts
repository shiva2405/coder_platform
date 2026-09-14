const USER_CACHE_KEY = 'coder-platform:user';
const DASHBOARD_CACHE_KEY = 'coder-platform:dashboard';

export function cacheUser(user: unknown): void {
  try {
    localStorage.setItem(USER_CACHE_KEY, JSON.stringify(user));
  } catch {
    // Ignore quota / private-mode failures
  }
}

export function readCachedUser<T>(): T | null {
  try {
    const raw = localStorage.getItem(USER_CACHE_KEY);
    return raw ? JSON.parse(raw) as T : null;
  } catch {
    return null;
  }
}

export function clearUserCaches(): void {
  try {
    localStorage.removeItem(USER_CACHE_KEY);
    localStorage.removeItem(DASHBOARD_CACHE_KEY);
    sessionStorage.removeItem(USER_CACHE_KEY);
    sessionStorage.removeItem(DASHBOARD_CACHE_KEY);
  } catch {
    // Ignore storage failures
  }
}
