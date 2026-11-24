import { areaForPath, navigation, navItemForPath, type Area, type NavItem } from '../navigation';
import type { Me, Role } from './session';

/**
 * Client-side view of the role matrix. It only decides what to show: every request is checked
 * again on the server, which also enforces care-unit membership and case assignment.
 */

const areaByRole: Record<Role, Area> = {
  coordinator: 'staff',
  reviewer: 'staff',
  patient: 'patient',
  caregiver: 'patient',
  admin: 'admin',
};

export const roleLabel: Record<Role, string> = {
  coordinator: 'Care coordinator',
  reviewer: 'Clinical reviewer',
  patient: 'Patient (demo)',
  caregiver: 'Caregiver (demo)',
  admin: 'Administrator',
};

export function areaFor(me: Me): Area {
  return areaByRole[me.role];
}

/** Links of an area the account may open; nothing for areas that are not its own. */
export function visibleNav(me: Me, area: Area): NavItem[] {
  if (areaFor(me) !== area) return [];
  return navigation[area].filter((item) => me.permissions.includes(item.permission));
}

/** First page of the account's own area, or the start page when it has none. */
export function homePath(me: Me): string {
  return visibleNav(me, areaFor(me))[0]?.path ?? '/';
}

export type Access = 'allowed' | 'wrong-area' | 'missing-permission';

export function accessTo(me: Me, pathname: string): Access {
  const area = areaForPath(pathname);
  if (area === undefined) return 'allowed';
  if (area !== areaFor(me)) return 'wrong-area';
  const item = navItemForPath(pathname);
  if (item && !me.permissions.includes(item.permission)) return 'missing-permission';
  return 'allowed';
}

/** Only same-origin paths survive, so `?next=` cannot send people to another site. */
export function safeNextPath(raw: string | null): string | undefined {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//') || raw.startsWith('/\\')) return undefined;
  if (/[\u0000-\u001f]/.test(raw)) return undefined;
  return raw;
}

/** Where to go after sign-in: the requested page if this account may open it, else its home. */
export function postSignInPath(me: Me, next: string | null): string {
  const target = safeNextPath(next);
  if (target && target !== '/' && !target.startsWith('/sign-in') && accessTo(me, target) === 'allowed') {
    return target;
  }
  return homePath(me);
}
