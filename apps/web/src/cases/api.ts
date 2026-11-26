import { apiFetch } from '../api/client';
import { toSearchParams, type CaseFilters } from './query';
import type {
  CaseEventView,
  CaseListPage,
  CaseStatus,
  CaseView,
  CreateCaseRequest,
  MemberView,
  UpdateCaseRequest,
} from './types';

/** The ETag the API uses for a case version; writes send it back in `If-Match`. */
export function versionTag(version: number): string {
  return `"v${version}"`;
}

export function listCases(filters: CaseFilters): Promise<CaseListPage> {
  const query = toSearchParams(filters, { forApi: true }).toString();
  return apiFetch<CaseListPage>(`/api/v1/cases${query ? `?${query}` : ''}`);
}

export function getCase(id: string): Promise<CaseView> {
  return apiFetch<CaseView>(`/api/v1/cases/${encodeURIComponent(id)}`);
}

export function caseHistory(id: string): Promise<CaseEventView[]> {
  return apiFetch<CaseEventView[]>(`/api/v1/cases/${encodeURIComponent(id)}/events`);
}

export function createCase(request: CreateCaseRequest): Promise<CaseView> {
  return apiFetch<CaseView>('/api/v1/cases', { method: 'POST', body: request });
}

export function updateCase(id: string, version: number, changes: UpdateCaseRequest): Promise<CaseView> {
  return apiFetch<CaseView>(`/api/v1/cases/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: changes,
    headers: { 'If-Match': versionTag(version) },
  });
}

export function changeCaseStatus(id: string, version: number, status: CaseStatus): Promise<CaseView> {
  return apiFetch<CaseView>(`/api/v1/cases/${encodeURIComponent(id)}/status`, {
    method: 'POST',
    body: { status },
    headers: { 'If-Match': versionTag(version) },
  });
}

export function unitReviewers(unitId: string): Promise<MemberView[]> {
  return apiFetch<MemberView[]>(`/api/v1/care-units/${encodeURIComponent(unitId)}/reviewers`);
}
