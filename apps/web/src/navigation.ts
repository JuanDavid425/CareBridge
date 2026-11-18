export type Area = 'staff' | 'patient';

export interface NavItem {
  path: string;
  label: string;
  description: string;
}

/**
 * Navigation per workspace. The patient area is a separate tree so that
 * no staff screen (drafts, proposals, audit) is ever linked from it.
 */
export const navigation: Record<Area, NavItem[]> = {
  staff: [
    {
      path: '/staff/cases',
      label: 'Cases',
      description: 'Upcoming discharges and assigned synthetic cases.',
    },
    {
      path: '/staff/review',
      label: 'Review queue',
      description: 'Proposed tasks waiting for a clinical reviewer.',
    },
    {
      path: '/staff/plans',
      label: 'Care plans',
      description: 'Task owners, due dates and publish status.',
    },
  ],
  patient: [
    {
      path: '/patient/tasks',
      label: 'Your next steps',
      description: 'Approved tasks from your published care plan.',
    },
  ],
};

export function areaForPath(pathname: string): Area | undefined {
  if (pathname === '/staff' || pathname.startsWith('/staff/')) return 'staff';
  if (pathname === '/patient' || pathname.startsWith('/patient/')) return 'patient';
  return undefined;
}
