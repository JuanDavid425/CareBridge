export type Area = 'staff' | 'patient' | 'admin';

export interface NavItem {
  path: string;
  label: string;
  description: string;
  /** Role-matrix permission needed to see the link. The server checks it again on every request. */
  permission: string;
}

/**
 * Navigation per workspace. The patient area is a separate tree so that no staff screen
 * (drafts, proposals, audit) is ever linked from it, and the admin area never links case content.
 */
export const navigation: Record<Area, NavItem[]> = {
  staff: [
    {
      path: '/staff/cases',
      label: 'Cases',
      description: 'Upcoming discharges and assigned synthetic cases.',
      permission: 'case.read',
    },
    {
      path: '/staff/review',
      label: 'Review queue',
      description: 'Proposed tasks waiting for a clinical reviewer.',
      permission: 'proposal.review',
    },
    {
      path: '/staff/plans',
      label: 'Care plans',
      description: 'Task owners, due dates and publish status.',
      permission: 'case.read',
    },
  ],
  patient: [
    {
      path: '/patient/tasks',
      label: 'Your next steps',
      description: 'Approved tasks from your published care plan.',
      permission: 'plan.read_published',
    },
  ],
  admin: [
    {
      path: '/admin/roles',
      label: 'Role matrix',
      description: 'What each role may do, and whether it applies per unit, per own case or instance-wide.',
      permission: 'user.manage',
    },
  ],
};

export const areas = Object.keys(navigation) as Area[];

export function areaForPath(pathname: string): Area | undefined {
  return areas.find((area) => pathname === `/${area}` || pathname.startsWith(`/${area}/`));
}

/** The navigation entry a path belongs to (the entry itself or a page below it). */
export function navItemForPath(pathname: string): NavItem | undefined {
  const area = areaForPath(pathname);
  if (!area) return undefined;
  return navigation[area].find((item) => pathname === item.path || pathname.startsWith(`${item.path}/`));
}
