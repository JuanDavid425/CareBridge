/** Wire types of the case registry API (`/api/v1/cases`). Every case is synthetic. */

export const caseStatuses = ['open', 'planning', 'review', 'active', 'closed'] as const;
export type CaseStatus = (typeof caseStatuses)[number];

export const dueFilters = ['overdue', 'today', 'week'] as const;
export type DueFilter = (typeof dueFilters)[number];

export const ageBands = ['18-29', '30-39', '40-49', '50-59', '60-69', '70-79', '80+'] as const;

export interface UnitRef {
  id: string;
  code: string;
  name: string;
}

export interface PatientView {
  reference: string;
  displayName: string;
  ageBand: string;
}

export interface MemberView {
  id: string;
  displayName: string;
  role: 'coordinator' | 'reviewer';
}

export interface CaseListItem {
  id: string;
  reference: string;
  careUnit: UnitRef;
  patient: PatientView;
  status: CaseStatus;
  dischargeDate: string;
  followUpDue: string;
  overdue: boolean;
  version: number;
}

export interface CaseListPage {
  items: CaseListItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface CaseView extends CaseListItem {
  summary: string;
  careTeam: MemberView[];
  allowedTransitions: CaseStatus[];
  canEdit: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CaseEventView {
  id: number;
  type: 'created' | 'updated' | 'status_changed' | 'seeded';
  actor: string;
  fromStatus: CaseStatus | null;
  toStatus: CaseStatus | null;
  changedFields: string[];
  version: number;
  occurredAt: string;
}

export interface CreateCaseRequest {
  careUnitId: string;
  patientName: string;
  ageBand: string;
  dischargeDate: string;
  followUpDue: string;
  summary: string;
  reviewerId: string | null;
}

export interface UpdateCaseRequest {
  dischargeDate?: string;
  followUpDue?: string;
  summary?: string;
}
