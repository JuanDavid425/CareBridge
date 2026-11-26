import { describe, expect, it } from 'vitest';
import { emptyFilters, hasActiveFilters, listMessage, parseFilters, toSearchParams } from './query';

describe('parseFilters', () => {
  it('reads search, statuses, due filter and a 1-based page from the URL', () => {
    const filters = parseFilters(new URLSearchParams('search=%20ruiz%20&status=review,open&due=overdue&page=3'));
    expect(filters).toEqual({ search: 'ruiz', statuses: ['open', 'review'], due: 'overdue', page: 2 });
  });

  it('drops values the API would reject instead of failing', () => {
    const filters = parseFilters(new URLSearchParams('status=archived&status=planning&due=soon&page=-4'));
    expect(filters).toEqual({ search: '', statuses: ['planning'], due: '', page: 0 });
    expect(parseFilters(new URLSearchParams('page=abc')).page).toBe(0);
    expect(parseFilters(new URLSearchParams(`search=${'x'.repeat(200)}`)).search).toHaveLength(80);
  });
});

describe('toSearchParams', () => {
  it('leaves defaults out of the page URL', () => {
    expect(toSearchParams(emptyFilters).toString()).toBe('');
    expect(toSearchParams({ search: 'a b', statuses: ['open'], due: 'week', page: 1 }).toString()).toBe(
      'search=a+b&status=open&due=week&page=2',
    );
  });

  it('sends zero-based pages and the page size to the API', () => {
    expect(toSearchParams({ ...emptyFilters, page: 1 }, { forApi: true }).toString()).toBe('page=1&size=20');
  });

  it('round-trips through the URL', () => {
    const filters = { search: 'Case 008', statuses: ['planning', 'active'] as const, due: 'today' as const, page: 4 };
    expect(parseFilters(toSearchParams({ ...filters, statuses: [...filters.statuses] }))).toEqual({
      ...filters,
      statuses: ['planning', 'active'],
    });
  });
});

describe('listMessage', () => {
  const none = { totalItems: 0, items: [] };

  it('shows loading and error states first', () => {
    expect(listMessage('loading', emptyFilters)).toBe('loading');
    expect(listMessage('error', emptyFilters)).toBe('error');
    expect(listMessage('ready', emptyFilters)).toBe('error');
  });

  it('tells "nothing assigned" apart from "nothing matches"', () => {
    expect(listMessage('ready', emptyFilters, none)).toBe('empty');
    expect(listMessage('ready', { ...emptyFilters, due: 'overdue' }, none)).toBe('no-matches');
    expect(hasActiveFilters({ ...emptyFilters, search: '   ' })).toBe(false);
  });

  it('notices a page past the end of a non-empty list', () => {
    expect(listMessage('ready', { ...emptyFilters, page: 9 }, { totalItems: 3, items: [] })).toBe('page-out-of-range');
    expect(listMessage('ready', emptyFilters, { totalItems: 1, items: [{}] })).toBe('results');
  });
});
