import { describe, expect, it } from 'vitest';
import {
  addDays,
  bareName,
  initialValues,
  serverErrors,
  toCreateRequest,
  todayIso,
  validateCaseForm,
  validateDates,
  type CaseFormValues,
} from './form';

const today = '2026-03-02';
const valid: CaseFormValues = {
  ...initialValues('unit-1', today),
  patientName: 'Ana Ruiz',
  ageBand: '50-59',
};

describe('dates', () => {
  it('counts days in UTC and across month ends', () => {
    expect(todayIso(new Date('2026-03-02T23:30:00Z'))).toBe('2026-03-02');
    expect(addDays('2026-02-27', 3)).toBe('2026-03-02');
    expect(addDays('2026-03-02', -30)).toBe('2026-01-31');
  });

  it('defaults the follow-up to a week after discharge', () => {
    expect(initialValues('unit-1', today)).toMatchObject({ dischargeDate: today, followUpDue: '2026-03-09' });
  });

  it('applies the same windows as the API', () => {
    expect(validateDates('2026-01-31', '2026-01-31', today)).toEqual({});
    expect(validateDates('2026-01-30', '2026-02-01', today).dischargeDate).toMatch(/30 days ago/);
    expect(validateDates('2026-05-01', '2026-05-01', today)).toEqual({});
    expect(validateDates('2026-05-02', '2026-05-02', today).dischargeDate).toBeDefined();
    expect(validateDates('2026-03-02', '2026-03-01', today).followUpDue).toMatch(/before discharge/);
    expect(validateDates('2026-03-02', '2026-05-31', today)).toEqual({});
    expect(validateDates('2026-03-02', '2026-06-01', today).followUpDue).toMatch(/90 days/);
    expect(validateDates('', '2026-02-30', today)).toEqual({
      dischargeDate: 'Enter the planned or actual discharge date.',
      followUpDue: 'Enter the follow-up due date.',
    });
  });
});

describe('validateCaseForm', () => {
  it('accepts a complete form', () => {
    expect(validateCaseForm(valid, today)).toEqual({});
  });

  it('reports each missing or invalid field', () => {
    const errors = validateCaseForm(
      { ...valid, careUnitId: '', patientName: '  (DEMO) ', ageBand: '15-17', summary: 'x'.repeat(2001) },
      today,
    );
    expect(Object.keys(errors).sort()).toEqual(['ageBand', 'careUnitId', 'patientName', 'summary']);
  });

  it('refuses digits in names so record numbers cannot slip in', () => {
    expect(validateCaseForm({ ...valid, patientName: 'MRN 4411' }, today).patientName).toMatch(/no numbers/);
    expect(validateCaseForm({ ...valid, patientName: "Zoë O'Neil-Brandt" }, today)).toEqual({});
  });
});

describe('requests and server errors', () => {
  it('strips the demo marker and sends null for no reviewer', () => {
    expect(bareName('  Ana   Ruiz (demo) ')).toBe('Ana Ruiz');
    expect(toCreateRequest({ ...valid, patientName: 'Ana Ruiz (DEMO)', summary: '  note ' })).toEqual({
      careUnitId: 'unit-1',
      patientName: 'Ana Ruiz',
      ageBand: '50-59',
      dischargeDate: today,
      followUpDue: '2026-03-09',
      summary: 'note',
      reviewerId: null,
    });
  });

  it('keeps only fields the form knows', () => {
    expect(serverErrors({ reviewerId: 'Choose a reviewer from this care unit.', other: 'x' })).toEqual({
      reviewerId: 'Choose a reviewer from this care unit.',
    });
  });
});
