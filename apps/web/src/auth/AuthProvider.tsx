import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ApiRequestError } from '../api/client';
import * as session from './session';
import type { Me } from './session';

export type AuthState =
  | { status: 'loading' }
  | { status: 'signed-out'; expired: boolean }
  | { status: 'signed-in'; me: Me }
  | { status: 'error'; message: string };

interface AuthContextValue {
  state: AuthState;
  signIn(handle: string, password: string): Promise<Me>;
  signOut(): Promise<void>;
  /** Call when any request answers 401: the session ended on the server. */
  sessionExpired(): void;
  reload(): void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    session
      .fetchMe()
      .then((me) => !cancelled && setState({ status: 'signed-in', me }))
      .catch((error: unknown) => {
        if (cancelled) return;
        if (error instanceof ApiRequestError && error.status === 401) {
          setState({ status: 'signed-out', expired: false });
        } else {
          setState({
            status: 'error',
            message: error instanceof Error ? error.message : 'Could not check your session.',
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  const signIn = useCallback(async (handle: string, password: string) => {
    const me = await session.signIn(handle, password);
    setState({ status: 'signed-in', me });
    return me;
  }, []);

  const signOut = useCallback(async () => {
    try {
      await session.signOut();
    } finally {
      setState({ status: 'signed-out', expired: false });
    }
  }, []);

  const sessionExpired = useCallback(() => setState({ status: 'signed-out', expired: true }), []);

  const reload = useCallback(() => {
    setState({ status: 'loading' });
    setAttempt((n) => n + 1);
  }, []);

  const value = useMemo(
    () => ({ state, signIn, signOut, sessionExpired, reload }),
    [state, signIn, signOut, sessionExpired, reload],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>');
  return value;
}

/** The signed-in account; only for components rendered behind {@link RequireArea}. */
export function useMe(): Me {
  const { state } = useAuth();
  if (state.status !== 'signed-in') throw new Error('useMe needs a signed-in account');
  return state.me;
}
