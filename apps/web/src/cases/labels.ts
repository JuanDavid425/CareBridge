import type { CaseEventView, CaseStatus, DueFilter } from './types';

export const statusLabel: Record<CaseStatus, string> = {
  open: 'Open',
  planning: 'Planning',
  review: 'In review',
  active: 'Active plan',
  closed: 'Closed',
};

/** Button text for moving a case to a status. */
export const transitionLabel: Record<CaseStatus, string> = {
  open: 'Reopen',
  planning: 'Start planning',
  review: 'Send for review',
  active: 'Approve and activate',
  closed: 'Close case',
};

/** Shown instead of "Start planning" when a reviewer sends a case back. */
export const returnToPlanningLabel = 'Request changes';

export const dueLabel: Record<DueFilter, string> = {
  overdue: 'Overdue',
  today: 'Due today',
  week: 'Due in the next 7 days',
};

const fieldLabel: Record<string, string> = {
  dischargeDate: 'discharge date',
  followUpDue: 'follow-up due date',
  summary: 'summary',
};

/** "12 Mar 2026" for an ISO date (yyyy-mm-dd), without time-zone shifts. */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  if (!year || !month || !day) return isoDate;
  return new Date(Date.UTC(year, month - 1, day)).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  });
}

export function describeEvent(event: CaseEventView): string {
  switch (event.type) {
    case 'created':
      return 'Case opened';
    case 'seeded':
      return 'Loaded from the synthetic demo seed';
    case 'status_changed':
      return `Status ${statusLabel[event.fromStatus ?? 'open']} → ${statusLabel[event.toStatus ?? 'open']}`;
    case 'updated':
      return `Changed ${event.changedFields.map((field) => fieldLabel[field] ?? field).join(', ') || 'nothing'}`;
  }
}
