import { useEffect, useState } from 'react';
import { ApiRequestError, apiFetch } from '../api/client';
import { homePath } from '../auth/access';
import { useAuth, useMe } from '../auth/AuthProvider';
import type { NavItem } from '../navigation';
import { AccessDenied } from './AccessDenied';

interface RoleMatrixEntry {
  role: string;
  permission: string;
  scope: 'unit' | 'own_case' | 'global';
}

const scopeLabel: Record<RoleMatrixEntry['scope'], string> = {
  unit: 'Assigned cases in own care units',
  own_case: 'Own linked case only',
  global: 'Instance-wide (no case content)',
};

type Load =
  | { status: 'loading' }
  | { status: 'ready'; rows: RoleMatrixEntry[] }
  | { status: 'denied'; requestId?: string }
  | { status: 'error'; message: string };

export function RoleMatrixPage({ item }: { item: NavItem }) {
  const me = useMe();
  const { sessionExpired } = useAuth();
  const [load, setLoad] = useState<Load>({ status: 'loading' });

  useEffect(() => {
    let cancelled = false;
    apiFetch<RoleMatrixEntry[]>('/api/v1/admin/role-matrix')
      .then((rows) => !cancelled && setLoad({ status: 'ready', rows }))
      .catch((error: unknown) => {
        if (cancelled) return;
        if (error instanceof ApiRequestError && error.status === 401) sessionExpired();
        else if (error instanceof ApiRequestError && error.status === 403)
          setLoad({ status: 'denied', requestId: error.requestId });
        else setLoad({ status: 'error', message: error instanceof Error ? error.message : 'Could not load.' });
      });
    return () => {
      cancelled = true;
    };
  }, [sessionExpired]);

  if (load.status === 'denied') {
    return <AccessDenied reason="server" home={homePath(me)} requestId={load.requestId} />;
  }

  return (
    <section>
      <h1>{item.label}</h1>
      <p className="section__lead">{item.description}</p>
      {load.status === 'loading' && <p role="status">Loading the role matrix…</p>}
      {load.status === 'error' && (
        <p className="form-error" role="alert">
          {load.message}
        </p>
      )}
      {load.status === 'ready' && (
        <table className="matrix">
          <thead>
            <tr>
              <th scope="col">Role</th>
              <th scope="col">Permission</th>
              <th scope="col">Applies to</th>
            </tr>
          </thead>
          <tbody>
            {load.rows.map((row) => (
              <tr key={`${row.role}:${row.permission}`}>
                <td>{row.role}</td>
                <td>
                  <code>{row.permission}</code>
                </td>
                <td>{scopeLabel[row.scope]}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
