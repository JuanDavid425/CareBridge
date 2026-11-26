import { useCallback } from 'react';
import { ApiRequestError } from '../api/client';
import { useAuth } from '../auth/AuthProvider';

export type Failure =
  | { kind: 'denied'; requestId?: string }
  | { kind: 'not-found' }
  | { kind: 'error'; message: string; requestId?: string };

/**
 * Turns a failed request into what a page shows. A 401 ends the session (the shell then sends the
 * person to sign-in), so it returns undefined.
 */
export function useApiFailure(): (error: unknown) => Failure | undefined {
  const { sessionExpired } = useAuth();
  return useCallback(
    (error: unknown) => {
      if (error instanceof ApiRequestError) {
        if (error.status === 401) {
          sessionExpired();
          return undefined;
        }
        if (error.status === 403) return { kind: 'denied', requestId: error.requestId };
        if (error.status === 404) return { kind: 'not-found' };
        return { kind: 'error', message: error.message, requestId: error.requestId };
      }
      return { kind: 'error', message: 'Something went wrong. Please try again.' };
    },
    [sessionExpired],
  );
}
