import { Navigate, useLocation } from 'react-router';
import { AppShell } from '../layout/AppShell';
import type { Area } from '../navigation';
import { AccessDenied } from '../pages/AccessDenied';
import { areaFor, homePath } from './access';
import { useAuth } from './AuthProvider';

/** Signs people in before an area and turns them away from areas that are not theirs. */
export function RequireArea({ area }: { area: Area }) {
  const { state, reload } = useAuth();
  const location = useLocation();

  switch (state.status) {
    case 'loading':
      return (
        <p className="page-status" role="status">
          Checking your session…
        </p>
      );
    case 'error':
      return (
        <div className="page-status" role="alert">
          <p>{state.message}</p>
          <button type="button" onClick={reload}>
            Try again
          </button>
        </div>
      );
    case 'signed-out': {
      const next = encodeURIComponent(location.pathname + location.search);
      const expired = state.expired ? '&expired=1' : '';
      return <Navigate to={`/sign-in?next=${next}${expired}`} replace />;
    }
    case 'signed-in':
      if (areaFor(state.me) !== area) {
        return (
          <main className="page-status">
            <AccessDenied reason="wrong-area" home={homePath(state.me)} />
          </main>
        );
      }
      return <AppShell area={area} me={state.me} />;
  }
}
