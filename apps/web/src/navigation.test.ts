import { describe, expect, it } from 'vitest';
import { areaForPath, navigation } from './navigation';

describe('navigation', () => {
  it('keeps every staff link inside /staff', () => {
    for (const item of navigation.staff) {
      expect(areaForPath(item.path)).toBe('staff');
    }
  });

  it('never links a staff screen from the patient area', () => {
    for (const item of navigation.patient) {
      expect(areaForPath(item.path)).toBe('patient');
    }
  });

  it('does not treat look-alike prefixes as an area', () => {
    expect(areaForPath('/staffing')).toBeUndefined();
    expect(areaForPath('/patients')).toBeUndefined();
    expect(areaForPath('/')).toBeUndefined();
  });

  it('uses unique paths', () => {
    const paths = [...navigation.staff, ...navigation.patient].map((i) => i.path);
    expect(new Set(paths).size).toBe(paths.length);
  });
});
