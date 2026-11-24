import { Link } from 'react-router';
import { homePath, roleLabel } from '../auth/access';
import { useAuth } from '../auth/AuthProvider';
import { DemoBanner } from '../layout/DemoBanner';

export function Landing() {
  const { state } = useAuth();

  return (
    <div className="landing">
      <DemoBanner />
      <main>
        <h1>CareBridge</h1>
        <p>Post-discharge care coordination with clinician-reviewed task lists.</p>
        {state.status === 'signed-in' ? (
          <p>
            Signed in as {state.me.displayName} ({roleLabel[state.me.role]}).{' '}
            <Link to={homePath(state.me)}>Continue to your workspace</Link>
          </p>
        ) : (
          <p>
            <Link to="/sign-in">Sign in</Link>
          </p>
        )}
        <ul className="landing__choices">
          <li>
            <strong>Care team workspace</strong>
            <span>Coordinators and clinical reviewers, for cases in their own care units</span>
          </li>
          <li>
            <strong>Patient portal</strong>
            <span>Patients and caregivers (demo accounts), for published tasks only</span>
          </li>
          <li>
            <strong>Administration</strong>
            <span>Accounts, care units and the role matrix; no case content</span>
          </li>
        </ul>
      </main>
    </div>
  );
}
