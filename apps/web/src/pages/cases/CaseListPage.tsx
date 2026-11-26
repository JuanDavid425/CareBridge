import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { homePath } from '../../auth/access';
import { useMe } from '../../auth/AuthProvider';
import { listCases } from '../../cases/api';
import { dueLabel, formatDate, statusLabel } from '../../cases/labels';
import {
  emptyFilters,
  listMessage,
  MAX_SEARCH,
  parseFilters,
  toSearchParams,
  type CaseFilters,
} from '../../cases/query';
import { caseStatuses, dueFilters, type CaseListPage as Page, type CaseStatus, type DueFilter } from '../../cases/types';
import { useApiFailure, type Failure } from '../../cases/useApiFailure';
import type { NavItem } from '../../navigation';
import { AccessDenied } from '../AccessDenied';

type Load = { status: 'loading' } | { status: 'ready'; page: Page } | { status: 'failed'; failure: Failure };

export function CaseListPage({ item }: { item: NavItem }) {
  const me = useMe();
  const toFailure = useApiFailure();
  const [params, setParams] = useSearchParams();
  const filters = parseFilters(params);
  const filterKey = toSearchParams(filters).toString();
  const [load, setLoad] = useState<Load>({ status: 'loading' });
  const [search, setSearch] = useState(filters.search);
  const [attempt, setAttempt] = useState(0);

  // Back/forward navigation changes the URL; keep the search box in step with it.
  useEffect(() => setSearch(filters.search), [filters.search]);

  useEffect(() => {
    let cancelled = false;
    setLoad({ status: 'loading' });
    listCases(parseFilters(new URLSearchParams(filterKey)))
      .then((page) => !cancelled && setLoad({ status: 'ready', page }))
      .catch((error: unknown) => {
        const failure = toFailure(error);
        if (!cancelled && failure) setLoad({ status: 'failed', failure });
      });
    return () => {
      cancelled = true;
    };
  }, [filterKey, attempt, toFailure]);

  function apply(next: CaseFilters) {
    // Synchronous so controlled inputs (status checkboxes) never show the old value.
    setParams(toSearchParams(next), { flushSync: true });
  }

  function onSearch(event: FormEvent) {
    event.preventDefault();
    apply({ ...filters, search, page: 0 });
  }

  function toggleStatus(status: CaseStatus, checked: boolean) {
    const statuses = checked ? [...filters.statuses, status] : filters.statuses.filter((s) => s !== status);
    apply({ ...filters, statuses: caseStatuses.filter((s) => statuses.includes(s)), page: 0 });
  }

  if (load.status === 'failed' && load.failure.kind === 'denied') {
    return <AccessDenied reason="server" home={homePath(me)} requestId={load.failure.requestId} />;
  }

  const message = listMessage(
    load.status === 'loading' ? 'loading' : load.status === 'failed' ? 'error' : 'ready',
    filters,
    load.status === 'ready' ? load.page : undefined,
  );

  return (
    <section className="cases">
      <div className="cases__header">
        <div>
          <h1>{item.label}</h1>
          <p className="section__lead">{item.description}</p>
        </div>
        {me.permissions.includes('case.create') && (
          <Link className="button-link" to="/staff/cases/new">
            New case
          </Link>
        )}
      </div>

      <form className="filters" role="search" aria-label="Filter cases" onSubmit={onSearch}>
        <div className="filters__search">
          <label htmlFor="case-search">Search</label>
          <input
            id="case-search"
            type="search"
            value={search}
            maxLength={MAX_SEARCH}
            placeholder="Case reference or patient name"
            onChange={(event) => setSearch(event.target.value)}
          />
          <button type="submit">Search</button>
        </div>
        <fieldset className="filters__statuses">
          <legend>Status</legend>
          {caseStatuses.map((status) => (
            <label key={status}>
              <input
                type="checkbox"
                checked={filters.statuses.includes(status)}
                onChange={(event) => toggleStatus(status, event.target.checked)}
              />
              {statusLabel[status]}
            </label>
          ))}
        </fieldset>
        <div className="filters__due">
          <label htmlFor="case-due">Follow-up</label>
          <select
            id="case-due"
            value={filters.due}
            onChange={(event) => apply({ ...filters, due: event.target.value as DueFilter | '', page: 0 })}
          >
            <option value="">Any due date</option>
            {dueFilters.map((due) => (
              <option key={due} value={due}>
                {dueLabel[due]}
              </option>
            ))}
          </select>
        </div>
        {(filters.search || filters.statuses.length > 0 || filters.due) && (
          <button
            type="button"
            className="button-secondary"
            onClick={() => {
              setSearch('');
              apply(emptyFilters);
            }}
          >
            Clear filters
          </button>
        )}
      </form>

      {message === 'loading' && (
        <div className="cases__loading" role="status" aria-live="polite">
          <span className="visually-hidden">Loading cases…</span>
          {[0, 1, 2].map((row) => (
            <div key={row} className="skeleton-row" aria-hidden="true" />
          ))}
        </div>
      )}
      {message === 'error' && load.status === 'failed' && (
        <div className="form-error" role="alert">
          {load.failure.kind === 'error' ? load.failure.message : 'Could not load cases.'}{' '}
          <button type="button" className="button-secondary" onClick={() => setAttempt((n) => n + 1)}>
            Try again
          </button>
        </div>
      )}
      {message === 'empty' && <p className="empty-state">No cases are assigned to you yet.</p>}
      {message === 'no-matches' && <p className="empty-state">No assigned cases match these filters.</p>}
      {message === 'page-out-of-range' && (
        <p className="empty-state">
          This page is past the end of the list.{' '}
          <button type="button" className="button-secondary" onClick={() => apply({ ...filters, page: 0 })}>
            Back to the first page
          </button>
        </p>
      )}

      {message === 'results' && load.status === 'ready' && (
        <>
          <table className="case-table">
            <caption className="visually-hidden">Assigned synthetic cases, earliest follow-up first</caption>
            <thead>
              <tr>
                <th scope="col">Case</th>
                <th scope="col">Patient (synthetic)</th>
                <th scope="col">Care unit</th>
                <th scope="col">Status</th>
                <th scope="col">Discharge</th>
                <th scope="col">Follow-up due</th>
              </tr>
            </thead>
            <tbody>
              {load.page.items.map((c) => (
                <tr key={c.id}>
                  <td data-label="Case">
                    <Link to={`/staff/cases/${c.id}`}>{c.reference}</Link>
                  </td>
                  <td data-label="Patient">{c.patient.displayName}</td>
                  <td data-label="Care unit">{c.careUnit.name}</td>
                  <td data-label="Status">
                    <span className={`status status--${c.status}`}>{statusLabel[c.status]}</span>
                  </td>
                  <td data-label="Discharge">{formatDate(c.dischargeDate)}</td>
                  <td data-label="Follow-up due">
                    {formatDate(c.followUpDue)}
                    {c.overdue && <span className="overdue"> · Overdue</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav className="pager" aria-label="Case list pages">
            <button
              type="button"
              className="button-secondary"
              disabled={filters.page === 0}
              onClick={() => apply({ ...filters, page: filters.page - 1 })}
            >
              Previous
            </button>
            <span>
              Page {load.page.page + 1} of {Math.max(load.page.totalPages, 1)} · {load.page.totalItems}{' '}
              {load.page.totalItems === 1 ? 'case' : 'cases'}
            </span>
            <button
              type="button"
              className="button-secondary"
              disabled={load.page.page + 1 >= load.page.totalPages}
              onClick={() => apply({ ...filters, page: filters.page + 1 })}
            >
              Next
            </button>
          </nav>
        </>
      )}
    </section>
  );
}
