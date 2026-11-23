import { useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { accessTo, homePath, roleLabel, visibleNav } from '../auth/access';
import { useAuth } from '../auth/AuthProvider';
import type { Me } from '../auth/session';
import type { Area } from '../navigation';
import { AccessDenied } from '../pages/AccessDenied';
import { DemoBanner } from './DemoBanner';

const areaTitle: Record<Area, string> = {
  staff: 'Care team workspace',
  patient: 'Patient portal',
  admin: 'Administration',
};

export function AppShell({ area, me }: { area: Area; me: Me }) {
  const { signOut } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [signingOut, setSigningOut] = useState(false);
  const items = visibleNav(me, area);
  const access = accessTo(me, location.pathname);

  async function onSignOut() {
    setSigningOut(true);
    try {
      await signOut();
    } finally {
      navigate('/sign-in', { replace: true });
    }
  }

  return (
    <div className={`shell shell--${area}`}>
      <DemoBanner />
      <header className="shell__header">
        <NavLink to={homePath(me)} className="shell__brand">
          CareBridge
        </NavLink>
        <span className="shell__area">{areaTitle[area]}</span>
        <div className="shell__account">
          <span>
            {me.displayName} · {roleLabel[me.role]}
            {me.units.length > 0 && <> · {me.units.map((unit) => unit.name).join(', ')}</>}
          </span>
          <button type="button" onClick={onSignOut} disabled={signingOut}>
            Sign out
          </button>
        </div>
      </header>
      <nav className="shell__nav" aria-label={areaTitle[area]}>
        {items.length > 0 ? (
          <ul>
            {items.map((item) => (
              <li key={item.path}>
                <NavLink to={item.path}>{item.label}</NavLink>
              </li>
            ))}
          </ul>
        ) : (
          <p className="shell__nav-empty">No pages are available to your account yet.</p>
        )}
      </nav>
      <main className="shell__main">
        {access === 'allowed' ? <Outlet /> : <AccessDenied reason={access} home={homePath(me)} />}
      </main>
    </div>
  );
}
