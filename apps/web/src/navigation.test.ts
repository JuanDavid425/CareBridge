import { describe, expect, it } from 'vitest';
import { areaForPath, areas, navigation, navItemForPath } from './navigation';

describe('navigation', () => {
  it('keeps every link inside its own area', () => {
    for (const area of areas) {
      for (const item of navigation[area]) {
        expect(areaForPath(item.path)).toBe(area);
      }
    }
  });

  it('does not treat look-alike prefixes as an area', () => {
    expect(areaForPath('/staffing')).toBeUndefined();
    expect(areaForPath('/patients')).toBeUndefined();
    expect(areaForPath('/administrator')).toBeUndefined();
    expect(areaForPath('/')).toBeUndefined();
  });

  it('uses unique paths', () => {
    const paths = areas.flatMap((area) => navigation[area].map((i) => i.path));
    expect(new Set(paths).size).toBe(paths.length);
  });

  it('gives every link a role-matrix permission', () => {
    for (const area of areas) {
      for (const item of navigation[area]) {
        expect(item.permission).toMatch(/^[a-z]+(\.[a-z_]+)+$/);
      }
    }
  });

  it('never gates patient links on staff permissions', () => {
    expect(navigation.patient.map((i) => i.permission)).toEqual(['plan.read_published']);
  });

  it('finds the entry for nested pages', () => {
    expect(navItemForPath('/staff/review/123')?.label).toBe('Review queue');
    expect(navItemForPath('/staff/reviewers')).toBeUndefined();
    expect(navItemForPath('/staff')).toBeUndefined();
  });
});
