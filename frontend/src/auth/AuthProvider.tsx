import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import {
  getCurrentUser,
  githubLoginUrl,
  loginWithPassword,
  logoutCurrentUser,
  registerAccount,
} from '../services/api';
import { cacheUser, clearUserCaches, readCachedUser } from '../services/authStorage';
import { User } from '../types';

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, name: string) => Promise<void>;
  logout: () => Promise<void>;
  loginWithGithub: (next?: string) => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(() => readCachedUser<User>());
  const [loading, setLoading] = useState(true);

  const applyUser = useCallback((next: User | null) => {
    setUser(next);
    if (next) {
      cacheUser(next);
    } else {
      clearUserCaches();
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const next = await getCurrentUser();
        if (!cancelled) {
          applyUser(next);
        }
      } catch {
        if (!cancelled) {
          applyUser(null);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [applyUser]);

  useEffect(() => {
    const onUnauthorized = () => {
      applyUser(null);
    };
    window.addEventListener('auth:unauthorized', onUnauthorized);
    return () => window.removeEventListener('auth:unauthorized', onUnauthorized);
  }, [applyUser]);

  const login = useCallback(async (email: string, password: string) => {
    applyUser(await loginWithPassword(email, password));
  }, [applyUser]);

  const register = useCallback(async (email: string, password: string, name: string) => {
    applyUser(await registerAccount(email, password, name));
  }, [applyUser]);

  const logout = useCallback(async () => {
    try {
      await logoutCurrentUser();
    } finally {
      applyUser(null);
    }
  }, [applyUser]);

  const loginWithGithub = useCallback((next = '/') => {
    window.location.href = githubLoginUrl(next);
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, register, logout, loginWithGithub }),
    [user, loading, login, register, logout, loginWithGithub],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return value;
}
