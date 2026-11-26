/**
 * Fetch wrapper for the CareBridge API: same-origin session cookie, CSRF header on every
 * state-changing request and the server's error envelope turned into {@link ApiRequestError}.
 */

export const CSRF_COOKIE = 'XSRF-TOKEN';
export const CSRF_HEADER = 'X-XSRF-TOKEN';

export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly requestId?: string,
    readonly fields: Record<string, string> = {},
  ) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

interface ErrorEnvelope {
  error: { code: string; message: string; requestId?: string; fields?: Record<string, string> };
}

function isErrorEnvelope(body: unknown): body is ErrorEnvelope {
  if (typeof body !== 'object' || body === null || !('error' in body)) return false;
  const error = (body as { error: unknown }).error;
  return (
    typeof error === 'object' &&
    error !== null &&
    typeof (error as { code?: unknown }).code === 'string' &&
    typeof (error as { message?: unknown }).message === 'string'
  );
}

/** Uses the envelope when the server sent one; proxies and crashes get a generic error. */
export function toApiError(status: number, body: unknown): ApiRequestError {
  if (isErrorEnvelope(body)) {
    const { code, message, requestId, fields } = body.error;
    return new ApiRequestError(status, code, message, requestId, fields ?? {});
  }
  return new ApiRequestError(status, `http_${status}`, 'Something went wrong. Please try again.');
}

export function readCookie(cookies: string, name: string): string | undefined {
  for (const part of cookies.split(';')) {
    const [key, ...rest] = part.trim().split('=');
    if (key === name) return decodeURIComponent(rest.join('='));
  }
  return undefined;
}

async function csrfToken(): Promise<string | undefined> {
  let token = readCookie(document.cookie, CSRF_COOKIE);
  if (!token) {
    await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' });
    token = readCookie(document.cookie, CSRF_COOKIE);
  }
  return token;
}

function parse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export interface ApiFetchOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Extra request headers, such as `If-Match` for versioned writes. */
  headers?: Record<string, string>;
}

export async function apiFetch<T>(path: string, { method = 'GET', body, headers: extra = {} }: ApiFetchOptions = {}): Promise<T> {
  const headers: Record<string, string> = { ...extra, Accept: 'application/json' };
  let response: Response;
  try {
    if (method !== 'GET') {
      const token = await csrfToken();
      if (token) headers[CSRF_HEADER] = token;
    }
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    response = await fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiRequestError(0, 'network_error', 'Could not reach CareBridge. Check your connection and try again.');
  }
  const text = await response.text();
  const data = text ? parse(text) : undefined;
  if (!response.ok) throw toApiError(response.status, data);
  return data as T;
}
