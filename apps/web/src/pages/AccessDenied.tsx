import { Link } from 'react-router';

export type DeniedReason = 'wrong-area' | 'missing-permission' | 'server';

const explanation: Record<DeniedReason, string> = {
  'wrong-area': 'This part of CareBridge is for a different kind of account.',
  'missing-permission': 'Your role does not include this page.',
  server: 'The server refused this request. You may not be assigned to this case or care unit.',
};

/** Denied state: says what happened and where to go, without hinting at what is behind it. */
export function AccessDenied({
  reason,
  home,
  requestId,
}: {
  reason: DeniedReason;
  home: string;
  requestId?: string;
}) {
  return (
    <section className="denied" aria-labelledby="denied-title">
      <h1 id="denied-title">You don’t have access to this page</h1>
      <p>{explanation[reason]}</p>
      <p>If you think this is wrong, ask an administrator to check your role and care units.</p>
      {requestId && (
        <p className="denied__ref">
          Reference: <code>{requestId}</code>
        </p>
      )}
      <Link to={home}>Go to your workspace</Link>
    </section>
  );
}
