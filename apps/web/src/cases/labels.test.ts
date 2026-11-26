import { describe, expect, it } from 'vitest';
import { versionTag } from './api';
import { describeEvent, formatDate } from './labels';
import type { CaseEventView } from './types';

const event = (overrides: Partial<CaseEventView>): CaseEventView => ({
  id: 1,
  type: 'created',
  actor: 'Maya Lindqvist (DEMO)',
  fromStatus: null,
  toStatus: null,
  changedFields: [],
  version: 1,
  occurredAt: '2026-03-02T09:00:00Z',
  ...overrides,
});

describe('labels', () => {
  it('formats ISO dates without shifting the day', () => {
    expect(formatDate('2026-03-02')).toBe('2 Mar 2026');
    expect(formatDate('not-a-date')).toBe('not-a-date');
  });

  it('describes history entries by field name, never by value', () => {
    expect(describeEvent(event({ type: 'status_changed', fromStatus: 'review', toStatus: 'active' }))).toBe(
      'Status In review → Active plan',
    );
    expect(describeEvent(event({ type: 'updated', changedFields: ['followUpDue', 'summary'] }))).toBe(
      'Changed follow-up due date, summary',
    );
    expect(describeEvent(event({ type: 'seeded' }))).toBe('Loaded from the synthetic demo seed');
  });

  it('uses the API ETag format for versions', () => {
    expect(versionTag(3)).toBe('"v3"');
  });
});
