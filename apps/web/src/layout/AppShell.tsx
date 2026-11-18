import { NavLink, Outlet } from 'react-router';
import { navigation, type Area } from '../navigation';
import { DemoBanner } from './DemoBanner';

const areaTitle: Record<Area, string> = {
  staff: 'Care team workspace',
  patient: 'Patient portal',
};

export function AppShell({ area }: { area: Area }) {
  return (
    <div className={`shell shell--${area}`}>
      <DemoBanner />
      <header className="shell__header">
        <NavLink to="/" className="shell__brand">
          CareBridge
        </NavLink>
        <span className="shell__area">{areaTitle[area]}</span>
      </header>
      <nav className="shell__nav" aria-label={areaTitle[area]}>
        <ul>
          {navigation[area].map((item) => (
            <li key={item.path}>
              <NavLink to={item.path}>{item.label}</NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <main className="shell__main">
        <Outlet />
      </main>
    </div>
  );
}
