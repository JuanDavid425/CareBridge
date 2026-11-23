import { describe, expect, it } from 'vitest';
import { accessTo, homePath, postSignInPath, safeNextPath, visibleNav } from './access';
import type { Me, Role } from './session';

// Mirrors the V3 role matrix (role_permissions).
const permissions: Record<Role, string[]> = {
  coordinator: ['audit.read', 'case.create', 'case.read', 'document.upload', 'task.assign', 'task.edit'],
  reviewer: ['audit.read', 'case.read', 'plan.publish', 'proposal.review'],
  patient: ['plan.read_published'],
  caregiver: ['plan.read_published'],
  admin: ['audit.read', 'unit.manage', 'user.manage'],
};

function account(role: Role): Me {
  return { id: role, handle: `${role}.demo`, displayName: role, role, units: [], permissions: permissions[role] };
}

const labels = (role: Role, area: 'staff' | 'patient' | 'admin') =>
  visibleNav(account(role), area).map((item) => item.label);

describe('role-aware navigation', () => {
  it('shows coordinators cases and plans but not the review queue', () => {
    expect(labels('coordinator', 'staff')).toEqual(['Cases', 'Care plans']);
  });

  it('shows reviewers the review queue', () => {
    expect(labels('reviewer', 'staff')).toEqual(['Cases', 'Review queue', 'Care plans']);
  });

  it('shows patients and caregivers only their portal', () => {
    for (const role of ['patient', 'caregiver'] as const) {
      expect(labels(role, 'patient')).toEqual(['Your next steps']);
      expect(labels(role, 'staff')).toEqual([]);
      expect(labels(role, 'admin')).toEqual([]);
    }
  });

  it('keeps administrators out of case screens', () => {
    expect(labels('admin', 'admin')).toEqual(['Role matrix']);
    expect(labels('admin', 'staff')).toEqual([]);
  });

  it('sends each role to its own home', () => {
    expect(homePath(account('coordinator'))).toBe('/staff/cases');
    expect(homePath(account('reviewer'))).toBe('/staff/cases');
    expect(homePath(account('caregiver'))).toBe('/patient/tasks');
    expect(homePath(account('admin'))).toBe('/admin/roles');
  });

  it('falls back to the start page when nothing is visible', () => {
    expect(homePath({ ...account('coordinator'), permissions: [] })).toBe('/');
  });
});

describe('denied states', () => {
  it('denies other areas', () => {
    expect(accessTo(account('patient'), '/staff/cases')).toBe('wrong-area');
    expect(accessTo(account('admin'), '/staff/review')).toBe('wrong-area');
    expect(accessTo(account('coordinator'), '/admin/roles')).toBe('wrong-area');
    expect(accessTo(account('reviewer'), '/patient/tasks')).toBe('wrong-area');
  });

  it('denies pages the role has no permission for', () => {
    expect(accessTo(account('coordinator'), '/staff/review')).toBe('missing-permission');
    expect(accessTo(account('coordinator'), '/staff/review/42')).toBe('missing-permission');
  });

  it('allows pages of the own area', () => {
    expect(accessTo(account('reviewer'), '/staff/review')).toBe('allowed');
    expect(accessTo(account('coordinator'), '/staff/cases')).toBe('allowed');
    expect(accessTo(account('patient'), '/')).toBe('allowed');
  });
});

describe('sign-in redirect', () => {
  it('only accepts same-origin paths', () => {
    expect(safeNextPath('/staff/cases?status=open')).toBe('/staff/cases?status=open');
    for (const raw of [null, '', 'staff', '//evil.example', '/\\evil.example', 'https://evil.example', '/x\ny']) {
      expect(safeNextPath(raw)).toBeUndefined();
    }
  });

  it('returns to the requested page when allowed', () => {
    expect(postSignInPath(account('reviewer'), '/staff/review')).toBe('/staff/review');
  });

  it('goes home instead of to a page the account cannot open', () => {
    expect(postSignInPath(account('coordinator'), '/staff/review')).toBe('/staff/cases');
    expect(postSignInPath(account('patient'), '/staff/cases')).toBe('/patient/tasks');
    expect(postSignInPath(account('admin'), '//evil.example')).toBe('/admin/roles');
    expect(postSignInPath(account('admin'), '/sign-in')).toBe('/admin/roles');
    expect(postSignInPath(account('admin'), null)).toBe('/admin/roles');
  });
});
