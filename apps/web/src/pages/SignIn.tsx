import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router';
import { ApiRequestError } from '../api/client';
import { homePath, postSignInPath } from '../auth/access';
import { useAuth } from '../auth/AuthProvider';
import { DemoBanner } from '../layout/DemoBanner';

function messageFor(error: unknown): string {
  if (!(error instanceof ApiRequestError)) return 'Something went wrong. Please try again.';
  switch (error.code) {
    case 'invalid_credentials':
      return 'Handle or password is incorrect.';
    case 'validation_failed':
      return 'Enter your handle and password.';
    case 'csrf_token_invalid':
      return 'Your sign-in form expired. Please try again.';
    default:
      return error.requestId ? `${error.message} (reference ${error.requestId})` : error.message;
  }
}

export function SignIn() {
  const { state, signIn } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [handle, setHandle] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string>();
  const [submitting, setSubmitting] = useState(false);

  if (state.status === 'signed-in' && !submitting) {
    return <Navigate to={homePath(state.me)} replace />;
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!handle.trim() || !password) {
      setError('Enter your handle and password.');
      return;
    }
    setSubmitting(true);
    setError(undefined);
    try {
      const me = await signIn(handle.trim(), password);
      navigate(postSignInPath(me, params.get('next')), { replace: true });
    } catch (caught) {
      setPassword('');
      setError(messageFor(caught));
      setSubmitting(false);
    }
  }

  return (
    <div className="sign-in">
      <DemoBanner />
      <main>
        <h1>Sign in to CareBridge</h1>
        {params.get('expired') && !error && (
          <p className="notice" role="status">
            Your session ended. Please sign in again.
          </p>
        )}
        <form onSubmit={onSubmit} noValidate aria-describedby={error ? 'sign-in-error' : undefined}>
          <label htmlFor="handle">Handle</label>
          <input
            id="handle"
            name="handle"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            value={handle}
            onChange={(e) => setHandle(e.target.value)}
            disabled={submitting}
          />
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={submitting}
          />
          {error && (
            <p id="sign-in-error" className="form-error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <p className="sign-in__hint">
          Demo accounts come from the synthetic seed. Their password is set by whoever runs the demo
          (<code>CAREBRIDGE_DEMO_PASSWORD</code>).
        </p>
      </main>
    </div>
  );
}
