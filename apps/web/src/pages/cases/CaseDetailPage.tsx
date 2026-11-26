import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { ApiRequestError } from '../../api/client';
import { homePath } from '../../auth/access';
import { useMe } from '../../auth/AuthProvider';
import { caseHistory, changeCaseStatus, getCase, updateCase } from '../../cases/api';
import { serverErrors, todayIso, validateDates, MAX_SUMMARY, type CaseFormErrors } from '../../cases/form';
import { describeEvent, formatDate, returnToPlanningLabel, statusLabel, transitionLabel } from '../../cases/labels';
import type { CaseEventView, CaseStatus, CaseView } from '../../cases/types';
import { useApiFailure, type Failure } from '../../cases/useApiFailure';
import { AccessDenied } from '../AccessDenied';

type Load =
  | { status: 'loading' }
  | { status: 'ready'; data: CaseView; history: CaseEventView[] }
  | { status: 'failed'; failure: Failure };

/** Problems from a write that the person needs to act on. */
type WriteProblem = { kind: 'stale' } | { kind: 'conflict' | 'error'; message: string } | undefined;

interface EditValues {
  dischargeDate: string;
  followUpDue: string;
  summary: string;
}

export function CaseDetailPage() {
  const { caseId = '' } = useParams();
  const me = useMe();
  const location = useLocation();
  const toFailure = useApiFailure();
  const created = (location.state as { created?: boolean } | null)?.created === true;
  const [load, setLoad] = useState<Load>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<WriteProblem>();
  const [notice, setNotice] = useState<string | undefined>(created ? 'Case opened.' : undefined);
  const [editing, setEditing] = useState<EditValues | undefined>();
  const [editErrors, setEditErrors] = useState<CaseFormErrors>({});

  const reload = useCallback(() => setAttempt((n) => n + 1), []);

  useEffect(() => {
    let cancelled = false;
    setLoad((current) => (current.status === 'ready' ? current : { status: 'loading' }));
    getCase(caseId)
      .then(async (data) => {
        const history = await caseHistory(caseId).catch(() => [] as CaseEventView[]);
        if (!cancelled) setLoad({ status: 'ready', data, history });
      })
      .catch((error: unknown) => {
        const failure = toFailure(error);
        if (!cancelled && failure) setLoad({ status: 'failed', failure });
      });
    return () => {
      cancelled = true;
    };
  }, [caseId, attempt, toFailure]);

  /** Shared handling for writes: stale versions, illegal moves and validation errors. */
  function writeFailed(error: unknown): void {
    if (error instanceof ApiRequestError) {
      if (error.status === 412) return setProblem({ kind: 'stale' });
      if (error.status === 409) return setProblem({ kind: 'conflict', message: error.message });
      if (error.status === 422) {
        setEditErrors(serverErrors(error.fields));
        return setProblem({ kind: 'error', message: 'Some fields need attention.' });
      }
    }
    const failure = toFailure(error);
    if (failure?.kind === 'denied') setLoad({ status: 'failed', failure });
    else if (failure) setProblem({ kind: 'error', message: failure.kind === 'error' ? failure.message : 'Not found.' });
  }

  async function move(data: CaseView, target: CaseStatus) {
    setBusy(true);
    setProblem(undefined);
    setNotice(undefined);
    try {
      await changeCaseStatus(data.id, data.version, target);
      setNotice(`Status changed to ${statusLabel[target]}.`);
      reload();
    } catch (error) {
      writeFailed(error);
    } finally {
      setBusy(false);
    }
  }

  async function save(event: FormEvent, data: CaseView) {
    event.preventDefault();
    if (!editing) return;
    const errors = validateDates(editing.dischargeDate, editing.followUpDue, todayIso());
    // Dates that were not touched may already be outside today's window; only check changed ones.
    if (editing.dischargeDate === data.dischargeDate) delete errors.dischargeDate;
    if (editing.dischargeDate === data.dischargeDate && editing.followUpDue === data.followUpDue) {
      delete errors.followUpDue;
    }
    if (editing.summary.trim().length > MAX_SUMMARY) errors.summary = `Use at most ${MAX_SUMMARY} characters.`;
    setEditErrors(errors);
    if (Object.keys(errors).length > 0) return;
    setBusy(true);
    setProblem(undefined);
    setNotice(undefined);
    try {
      await updateCase(data.id, data.version, {
        dischargeDate: editing.dischargeDate,
        followUpDue: editing.followUpDue,
        summary: editing.summary.trim(),
      });
      setEditing(undefined);
      setNotice('Changes saved.');
      reload();
    } catch (error) {
      writeFailed(error);
    } finally {
      setBusy(false);
    }
  }

  if (load.status === 'loading') {
    return (
      <p role="status" aria-live="polite">
        Loading case…
      </p>
    );
  }
  if (load.status === 'failed') {
    if (load.failure.kind === 'denied') {
      return <AccessDenied reason="server" home={homePath(me)} requestId={load.failure.requestId} />;
    }
    return (
      <section>
        <h1>{load.failure.kind === 'not-found' ? 'Case not found' : 'Could not load this case'}</h1>
        <p className="form-error" role="alert">
          {load.failure.kind === 'not-found'
            ? 'There is no case at this address.'
            : `${load.failure.message}`}
        </p>
        <Link to="/staff/cases">Back to cases</Link>
      </section>
    );
  }

  const { data, history } = load;
  return (
    <article className="case-detail" aria-labelledby="case-title">
      <p>
        <Link to="/staff/cases">← All cases</Link>
      </p>
      <h1 id="case-title">
        {data.reference} · {data.patient.displayName}
      </h1>
      <p className="section__lead">Synthetic patient. Every detail on this page is invented.</p>

      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}
      {problem?.kind === 'stale' && (
        <div className="form-error" role="alert">
          Someone else changed this case since you opened it. Your change was not saved.{' '}
          <button
            type="button"
            className="button-secondary"
            onClick={() => {
              setProblem(undefined);
              setEditing(undefined);
              reload();
            }}
          >
            Reload the case
          </button>
        </div>
      )}
      {(problem?.kind === 'conflict' || problem?.kind === 'error') && (
        <p className="form-error" role="alert">
          {problem.message}
        </p>
      )}

      <dl className="facts">
        <dt>Status</dt>
        <dd>
          <span className={`status status--${data.status}`}>{statusLabel[data.status]}</span>
        </dd>
        <dt>Care unit</dt>
        <dd>{data.careUnit.name}</dd>
        <dt>Age band</dt>
        <dd>{data.patient.ageBand}</dd>
        <dt>Discharge</dt>
        <dd>{formatDate(data.dischargeDate)}</dd>
        <dt>Follow-up due</dt>
        <dd>
          {formatDate(data.followUpDue)}
          {data.overdue && <span className="overdue"> · Overdue</span>}
        </dd>
        <dt>Care team</dt>
        <dd>
          <ul className="plain-list">
            {data.careTeam.map((member) => (
              <li key={member.id}>
                {member.displayName} ({member.role === 'coordinator' ? 'coordinator' : 'reviewer'})
              </li>
            ))}
          </ul>
        </dd>
        <dt>Summary</dt>
        <dd>{data.summary || <span className="muted">No summary yet.</span>}</dd>
      </dl>

      {data.allowedTransitions.length > 0 && (
        <section aria-labelledby="status-actions">
          <h2 id="status-actions">Next step</h2>
          <div className="actions">
            {data.allowedTransitions.map((target) => (
              <button key={target} type="button" disabled={busy} onClick={() => move(data, target)}>
                {data.status === 'review' && target === 'planning' ? returnToPlanningLabel : transitionLabel[target]}
              </button>
            ))}
          </div>
        </section>
      )}

      {data.canEdit && !editing && (
        <button
          type="button"
          className="button-secondary"
          onClick={() => {
            setEditErrors({});
            setEditing({ dischargeDate: data.dischargeDate, followUpDue: data.followUpDue, summary: data.summary });
          }}
        >
          Edit dates and summary
        </button>
      )}
      {editing && (
        <form className="case-form" onSubmit={(event) => save(event, data)} noValidate aria-labelledby="edit-title">
          <h2 id="edit-title">Edit case</h2>
          <DateField
            id="edit-discharge"
            label="Discharge date"
            value={editing.dischargeDate}
            error={editErrors.dischargeDate}
            onChange={(value) => setEditing({ ...editing, dischargeDate: value })}
          />
          <DateField
            id="edit-due"
            label="Follow-up due"
            value={editing.followUpDue}
            error={editErrors.followUpDue}
            onChange={(value) => setEditing({ ...editing, followUpDue: value })}
          />
          <label htmlFor="edit-summary">Summary (synthetic text only)</label>
          <textarea
            id="edit-summary"
            rows={4}
            maxLength={MAX_SUMMARY}
            value={editing.summary}
            aria-invalid={editErrors.summary ? true : undefined}
            aria-describedby={editErrors.summary ? 'edit-summary-error' : undefined}
            onChange={(event) => setEditing({ ...editing, summary: event.target.value })}
          />
          {editErrors.summary && (
            <p id="edit-summary-error" className="field-error">
              {editErrors.summary}
            </p>
          )}
          <div className="actions">
            <button type="submit" disabled={busy}>
              Save changes
            </button>
            <button type="button" className="button-secondary" onClick={() => setEditing(undefined)}>
              Cancel
            </button>
          </div>
        </form>
      )}

      <section aria-labelledby="history-title">
        <h2 id="history-title">History</h2>
        {history.length === 0 ? (
          <p className="muted">No history available.</p>
        ) : (
          <ol className="history">
            {history.map((event) => (
              <li key={event.id}>
                <span>{describeEvent(event)}</span>
                <span className="muted">
                  {' '}
                  · {event.actor} · version {event.version} ·{' '}
                  <time dateTime={event.occurredAt}>{new Date(event.occurredAt).toLocaleString('en-GB')}</time>
                </span>
              </li>
            ))}
          </ol>
        )}
      </section>
    </article>
  );
}

export function DateField({
  id,
  label,
  value,
  error,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  error?: string;
  onChange(value: string): void;
}) {
  return (
    <>
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        name={id}
        type="date"
        value={value}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      {error && (
        <p id={`${id}-error`} className="field-error">
          {error}
        </p>
      )}
    </>
  );
}
