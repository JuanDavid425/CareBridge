import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiRequestError } from '../../api/client';
import { homePath } from '../../auth/access';
import { useMe } from '../../auth/AuthProvider';
import { createCase, unitReviewers } from '../../cases/api';
import {
  initialValues,
  MAX_NAME,
  MAX_SUMMARY,
  serverErrors,
  todayIso,
  toCreateRequest,
  validateCaseForm,
  type CaseFormErrors,
  type CaseFormValues,
} from '../../cases/form';
import { ageBands, type MemberView } from '../../cases/types';
import { useApiFailure } from '../../cases/useApiFailure';
import { AccessDenied } from '../AccessDenied';
import { DateField } from './CaseDetailPage';

type Reviewers = { status: 'loading' } | { status: 'ready'; list: MemberView[] } | { status: 'failed' };

const fieldOrder: (keyof CaseFormValues)[] = [
  'careUnitId',
  'patientName',
  'ageBand',
  'dischargeDate',
  'followUpDue',
  'reviewerId',
  'summary',
];

export function CaseCreatePage() {
  const me = useMe();
  const navigate = useNavigate();
  const toFailure = useApiFailure();
  const units = me.units.filter((unit) => unit.role === 'coordinator');
  const [values, setValues] = useState<CaseFormValues>(() => initialValues(units[0]?.id ?? '', todayIso()));
  const [errors, setErrors] = useState<CaseFormErrors>({});
  const [reviewers, setReviewers] = useState<Reviewers>({ status: 'loading' });
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string>();
  const [denied, setDenied] = useState<string | undefined | false>(false);
  const formRef = useRef<HTMLFormElement>(null);

  useEffect(() => {
    if (!values.careUnitId) return;
    let cancelled = false;
    setReviewers({ status: 'loading' });
    unitReviewers(values.careUnitId)
      .then((list) => !cancelled && setReviewers({ status: 'ready', list }))
      .catch((error: unknown) => {
        toFailure(error);
        if (!cancelled) setReviewers({ status: 'failed' });
      });
    return () => {
      cancelled = true;
    };
  }, [values.careUnitId, toFailure]);

  if (!me.permissions.includes('case.create') || units.length === 0) {
    return <AccessDenied reason="missing-permission" home={homePath(me)} />;
  }
  if (denied !== false) {
    return <AccessDenied reason="server" home={homePath(me)} requestId={denied} />;
  }

  function set<K extends keyof CaseFormValues>(key: K, value: CaseFormValues[K]) {
    setValues((current) => ({ ...current, ...{ [key]: value }, ...(key === 'careUnitId' ? { reviewerId: '' } : {}) }));
  }

  function focusFirstError(found: CaseFormErrors) {
    const first = fieldOrder.find((key) => found[key]);
    if (first) formRef.current?.querySelector<HTMLElement>(`[name="${first}"]`)?.focus();
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(undefined);
    const found = validateCaseForm(values, todayIso());
    setErrors(found);
    if (Object.keys(found).length > 0) {
      focusFirstError(found);
      return;
    }
    setSubmitting(true);
    try {
      const created = await createCase(toCreateRequest(values));
      navigate(`/staff/cases/${created.id}`, { state: { created: true } });
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 422) {
        const fromServer = serverErrors(error.fields);
        setErrors(fromServer);
        setFormError('Some fields need attention.');
        focusFirstError(fromServer);
      } else {
        const failure = toFailure(error);
        if (failure?.kind === 'denied') setDenied(failure.requestId);
        else if (failure) setFormError(failure.kind === 'error' ? failure.message : 'Could not open the case.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  const describe = (key: keyof CaseFormValues) => (errors[key] ? `${key}-error` : undefined);
  const fieldError = (key: keyof CaseFormValues) =>
    errors[key] && (
      <p id={`${key}-error`} className="field-error">
        {errors[key]}
      </p>
    );

  return (
    <section aria-labelledby="new-case-title">
      <p>
        <Link to="/staff/cases">← All cases</Link>
      </p>
      <h1 id="new-case-title">New case</h1>
      <p className="notice">
        Synthetic data only. Use an invented name; “(DEMO)” is added to it automatically. Never enter real patient
        details.
      </p>
      {formError && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}
      <form ref={formRef} className="case-form" onSubmit={onSubmit} noValidate>
        <label htmlFor="careUnitId">Care unit</label>
        <select
          id="careUnitId"
          name="careUnitId"
          value={values.careUnitId}
          aria-describedby={describe('careUnitId')}
          onChange={(event) => set('careUnitId', event.target.value)}
        >
          {units.map((unit) => (
            <option key={unit.id} value={unit.id}>
              {unit.name}
            </option>
          ))}
        </select>
        {fieldError('careUnitId')}

        <label htmlFor="patientName">Patient name (invented)</label>
        <input
          id="patientName"
          name="patientName"
          value={values.patientName}
          maxLength={MAX_NAME + 7}
          autoComplete="off"
          aria-invalid={errors.patientName ? true : undefined}
          aria-describedby={describe('patientName')}
          onChange={(event) => set('patientName', event.target.value)}
        />
        {fieldError('patientName')}

        <label htmlFor="ageBand">Age band</label>
        <select
          id="ageBand"
          name="ageBand"
          value={values.ageBand}
          aria-invalid={errors.ageBand ? true : undefined}
          aria-describedby={describe('ageBand')}
          onChange={(event) => set('ageBand', event.target.value)}
        >
          <option value="">Choose…</option>
          {ageBands.map((band) => (
            <option key={band} value={band}>
              {band}
            </option>
          ))}
        </select>
        {fieldError('ageBand')}

        <DateField
          id="dischargeDate"
          label="Discharge date"
          value={values.dischargeDate}
          error={errors.dischargeDate}
          onChange={(value) => set('dischargeDate', value)}
        />
        <DateField
          id="followUpDue"
          label="Follow-up due"
          value={values.followUpDue}
          error={errors.followUpDue}
          onChange={(value) => set('followUpDue', value)}
        />

        <label htmlFor="reviewerId">Clinical reviewer (optional)</label>
        <select
          id="reviewerId"
          name="reviewerId"
          value={values.reviewerId}
          disabled={reviewers.status !== 'ready'}
          aria-describedby={describe('reviewerId')}
          onChange={(event) => set('reviewerId', event.target.value)}
        >
          <option value="">
            {reviewers.status === 'loading'
              ? 'Loading reviewers…'
              : reviewers.status === 'failed'
                ? 'Reviewers could not be loaded'
                : 'Assign later'}
          </option>
          {reviewers.status === 'ready' &&
            reviewers.list.map((reviewer) => (
              <option key={reviewer.id} value={reviewer.id}>
                {reviewer.displayName}
              </option>
            ))}
        </select>
        {fieldError('reviewerId')}

        <label htmlFor="summary">Summary (optional, synthetic text only)</label>
        <textarea
          id="summary"
          name="summary"
          rows={4}
          maxLength={MAX_SUMMARY}
          value={values.summary}
          aria-describedby={describe('summary')}
          onChange={(event) => set('summary', event.target.value)}
        />
        {fieldError('summary')}

        <div className="actions">
          <button type="submit" disabled={submitting}>
            {submitting ? 'Opening case…' : 'Open case'}
          </button>
          <Link to="/staff/cases">Cancel</Link>
        </div>
      </form>
    </section>
  );
}
