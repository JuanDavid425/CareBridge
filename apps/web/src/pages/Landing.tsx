import { Link } from 'react-router';
import { DemoBanner } from '../layout/DemoBanner';

export function Landing() {
  return (
    <div className="landing">
      <DemoBanner />
      <main>
        <h1>CareBridge</h1>
        <p>Post-discharge care coordination with clinician-reviewed task lists.</p>
        <ul className="landing__choices">
          <li>
            <Link to="/staff/cases">Care team workspace</Link>
            <span>Coordinators, reviewers and administrators</span>
          </li>
          <li>
            <Link to="/patient/tasks">Patient portal</Link>
            <span>Patients and caregivers (demo accounts)</span>
          </li>
        </ul>
      </main>
    </div>
  );
}
