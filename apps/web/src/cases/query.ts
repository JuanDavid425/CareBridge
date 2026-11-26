import { caseStatuses, dueFilters, type CaseStatus, type DueFilter } from './types';

/** Case list filters. They live in the page URL so a filtered list can be reloaded and shared. */
export interface CaseFilters {
  search: string;
  statuses: CaseStatus[];
  due: DueFilter | '';
  /** Zero-based, like the API. */
  page: number;
}

export const PAGE_SIZE = 20;
export const MAX_SEARCH = 80;

export const emptyFilters: CaseFilters = { search: '', statuses: [], due: '', page: 0 };

function isStatus(value: string): value is CaseStatus {
  return (caseStatuses as readonly string[]).includes(value);
}

function isDue(value: string): value is DueFilter {
  return (dueFilters as readonly string[]).includes(value);
}

/** Reads filters from the URL, dropping anything the API would reject instead of failing. */
export function parseFilters(params: URLSearchParams): CaseFilters {
  const statuses = params
    .getAll('status')
    .flatMap((value) => value.split(','))
    .map((value) => value.trim())
    .filter(isStatus);
  const due = params.get('due') ?? '';
  const page = Number.parseInt(params.get('page') ?? '', 10);
  return {
    search: (params.get('search') ?? '').trim().slice(0, MAX_SEARCH),
    statuses: caseStatuses.filter((status) => statuses.includes(status)),
    due: isDue(due) ? due : '',
    // The URL shows pages from 1; the API counts from 0.
    page: Number.isFinite(page) && page > 1 ? Math.min(page - 1, 9999) : 0,
  };
}

/**
 * Query string for the page URL (`forApi: false`, pages from 1, defaults left out) or for the API
 * (`forApi: true`, pages from 0, with the page size).
 */
export function toSearchParams(filters: CaseFilters, { forApi = false } = {}): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.search.trim()) params.set('search', filters.search.trim());
  if (filters.statuses.length > 0) params.set('status', filters.statuses.join(','));
  if (filters.due) params.set('due', filters.due);
  if (forApi) {
    params.set('page', String(filters.page));
    params.set('size', String(PAGE_SIZE));
  } else if (filters.page > 0) {
    params.set('page', String(filters.page + 1));
  }
  return params;
}

export function hasActiveFilters(filters: CaseFilters): boolean {
  return filters.search.trim() !== '' || filters.statuses.length > 0 || filters.due !== '';
}

export type ListMessage = 'loading' | 'error' | 'empty' | 'no-matches' | 'page-out-of-range' | 'results';

/** Which message the list shows; kept apart from the component so each state is tested. */
export function listMessage(
  state: 'loading' | 'error' | 'ready',
  filters: CaseFilters,
  result?: { totalItems: number; items: unknown[] },
): ListMessage {
  if (state === 'loading') return 'loading';
  if (state === 'error' || !result) return 'error';
  if (result.items.length > 0) return 'results';
  if (result.totalItems > 0) return 'page-out-of-range';
  return hasActiveFilters(filters) ? 'no-matches' : 'empty';
}
