import { apiFetch } from '../api/client';

export type Role = 'coordinator' | 'reviewer' | 'patient' | 'caregiver' | 'admin';

export interface Unit {
  id: string;
  code: string;
  name: string;
  role: Role;
}

/** `GET /api/v1/auth/me`: read fresh from the server, never trusted from storage. */
export interface Me {
  id: string;
  handle: string;
  displayName: string;
  role: Role;
  units: Unit[];
  permissions: string[];
}

export function fetchMe(): Promise<Me> {
  return apiFetch<Me>('/api/v1/auth/me');
}

export function signIn(handle: string, password: string): Promise<Me> {
  return apiFetch<Me>('/api/v1/auth/login', { method: 'POST', body: { handle, password } });
}

export async function signOut(): Promise<void> {
  await apiFetch<void>('/api/v1/auth/logout', { method: 'POST' });
}
