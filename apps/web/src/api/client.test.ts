import { describe, expect, it } from 'vitest';
import { readCookie, toApiError } from './client';

describe('toApiError', () => {
  it('keeps the server envelope', () => {
    const error = toApiError(403, {
      error: { code: 'forbidden', message: 'You do not have access to this resource.', requestId: 'req-1' },
    });
    expect(error.status).toBe(403);
    expect(error.code).toBe('forbidden');
    expect(error.message).toBe('You do not have access to this resource.');
    expect(error.requestId).toBe('req-1');
    expect(error.fields).toEqual({});
  });

  it('keeps validation fields', () => {
    const error = toApiError(422, {
      error: { code: 'validation_failed', message: 'Some fields are missing or invalid.', fields: { handle: 'must not be blank' } },
    });
    expect(error.fields).toEqual({ handle: 'must not be blank' });
  });

  it('falls back to a generic error for bodies that are not an envelope', () => {
    for (const body of [undefined, 'Bad Gateway', { error: 'nope' }, { error: { code: 1 } }]) {
      const error = toApiError(502, body);
      expect(error.code).toBe('http_502');
      expect(error.message).toBe('Something went wrong. Please try again.');
    }
  });
});

describe('readCookie', () => {
  it('finds a cookie among others', () => {
    expect(readCookie('a=1; XSRF-TOKEN=abc%3D%3D; b=2', 'XSRF-TOKEN')).toBe('abc==');
  });

  it('does not match on a prefix', () => {
    expect(readCookie('XSRF-TOKEN-OLD=x', 'XSRF-TOKEN')).toBeUndefined();
    expect(readCookie('', 'XSRF-TOKEN')).toBeUndefined();
  });
});
