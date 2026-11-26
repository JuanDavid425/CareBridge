import { ageBands, type CreateCaseRequest } from './types';

/** Create-case form as typed; every field is a string while editing. */
export interface CaseFormValues {
  careUnitId: string;
  patientName: string;
  ageBand: string;
  dischargeDate: string;
  followUpDue: string;
  summary: string;
  reviewerId: string;
}

export type CaseFormErrors = Partial<Record<keyof CaseFormValues, string>>;

// Mirrors CaseService on the server, which checks everything again.
export const DISCHARGE_PAST_DAYS = 30;
export const DISCHARGE_FUTURE_DAYS = 60;
export const FOLLOW_UP_WINDOW_DAYS = 90;
export const DEFAULT_FOLLOW_UP_DAYS = 7;
export const MAX_NAME = 100;
export const MAX_SUMMARY = 2000;

const NAME = /^\p{L}[\p{L} .'-]*$/u;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Today as yyyy-mm-dd in UTC, which is how the API counts days. */
export function todayIso(now: Date = new Date()): string {
  return now.toISOString().slice(0, 10);
}

export function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

export function initialValues(careUnitId: string, today: string): CaseFormValues {
  return {
    careUnitId,
    patientName: '',
    ageBand: '',
    dischargeDate: today,
    followUpDue: addDays(today, DEFAULT_FOLLOW_UP_DAYS),
    summary: '',
    reviewerId: '',
  };
}

/** "Ana Ruiz (demo)" → "Ana Ruiz": the API adds the (DEMO) marker itself. */
export function bareName(name: string): string {
  return name.trim().replace(/\s+/g, ' ').replace(/\s*\(demo\)\s*$/i, '').trim();
}

function isValidDate(value: string): boolean {
  return ISO_DATE.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`)) && addDays(value, 0) === value;
}

export function validateDates(dischargeDate: string, followUpDue: string, today: string): CaseFormErrors {
  const errors: CaseFormErrors = {};
  if (!isValidDate(dischargeDate)) {
    errors.dischargeDate = 'Enter the planned or actual discharge date.';
  } else if (dischargeDate < addDays(today, -DISCHARGE_PAST_DAYS) || dischargeDate > addDays(today, DISCHARGE_FUTURE_DAYS)) {
    errors.dischargeDate = `Use a date from ${DISCHARGE_PAST_DAYS} days ago to ${DISCHARGE_FUTURE_DAYS} days ahead.`;
  }
  if (!isValidDate(followUpDue)) {
    errors.followUpDue = 'Enter the follow-up due date.';
  } else if (!errors.dischargeDate && followUpDue < dischargeDate) {
    errors.followUpDue = 'The follow-up cannot be due before discharge.';
  } else if (!errors.dischargeDate && followUpDue > addDays(dischargeDate, FOLLOW_UP_WINDOW_DAYS)) {
    errors.followUpDue = `Use a date within ${FOLLOW_UP_WINDOW_DAYS} days of discharge.`;
  }
  return errors;
}

export function validateCaseForm(values: CaseFormValues, today: string): CaseFormErrors {
  const errors: CaseFormErrors = {};
  if (!values.careUnitId) errors.careUnitId = 'Choose a care unit.';
  const name = bareName(values.patientName);
  if (!name) errors.patientName = 'Enter an invented name for the synthetic patient.';
  else if (name.length > MAX_NAME) errors.patientName = `Use at most ${MAX_NAME} characters.`;
  else if (!NAME.test(name)) errors.patientName = 'Use letters, spaces, apostrophes and hyphens only; no numbers.';
  if (!(ageBands as readonly string[]).includes(values.ageBand)) errors.ageBand = 'Choose an age band.';
  Object.assign(errors, validateDates(values.dischargeDate, values.followUpDue, today));
  if (values.summary.trim().length > MAX_SUMMARY) errors.summary = `Use at most ${MAX_SUMMARY} characters.`;
  return errors;
}

export function toCreateRequest(values: CaseFormValues): CreateCaseRequest {
  return {
    careUnitId: values.careUnitId,
    patientName: bareName(values.patientName),
    ageBand: values.ageBand,
    dischargeDate: values.dischargeDate,
    followUpDue: values.followUpDue,
    summary: values.summary.trim(),
    reviewerId: values.reviewerId || null,
  };
}

/** Server field problems (422) onto the form; unknown fields are ignored. */
export function serverErrors(fields: Record<string, string>): CaseFormErrors {
  const known: (keyof CaseFormValues)[] = [
    'careUnitId',
    'patientName',
    'ageBand',
    'dischargeDate',
    'followUpDue',
    'summary',
    'reviewerId',
  ];
  const errors: CaseFormErrors = {};
  for (const key of known) {
    if (fields[key]) errors[key] = fields[key];
  }
  return errors;
}
