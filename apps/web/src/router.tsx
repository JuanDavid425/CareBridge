import { createBrowserRouter, Navigate } from 'react-router';
import { RequireArea } from './auth/RequireArea';
import { navigation, type NavItem } from './navigation';
import { Landing } from './pages/Landing';
import { NotFound } from './pages/NotFound';
import { RoleMatrixPage } from './pages/RoleMatrixPage';
import { SectionPage } from './pages/SectionPage';
import { SignIn } from './pages/SignIn';

function find(items: NavItem[], path: string): NavItem {
  const item = items.find((i) => i.path === path);
  if (!item) throw new Error(`Missing navigation entry for ${path}`);
  return item;
}

const staff = (path: string) => find(navigation.staff, path);
const patient = (path: string) => find(navigation.patient, path);
const admin = (path: string) => find(navigation.admin, path);

export const router = createBrowserRouter([
  { path: '/', element: <Landing /> },
  { path: '/sign-in', element: <SignIn /> },
  {
    path: '/staff',
    element: <RequireArea area="staff" />,
    children: [
      { index: true, element: <Navigate to="cases" replace /> },
      {
        path: 'cases',
        element: <SectionPage item={staff('/staff/cases')} emptyText="No cases assigned to you yet." />,
      },
      {
        path: 'review',
        element: <SectionPage item={staff('/staff/review')} emptyText="Nothing is waiting for review." />,
      },
      {
        path: 'plans',
        element: <SectionPage item={staff('/staff/plans')} emptyText="No care plans yet." />,
      },
    ],
  },
  {
    path: '/patient',
    element: <RequireArea area="patient" />,
    children: [
      { index: true, element: <Navigate to="tasks" replace /> },
      {
        path: 'tasks',
        element: (
          <SectionPage
            item={patient('/patient/tasks')}
            emptyText="Your care team has not published any tasks yet."
          />
        ),
      },
    ],
  },
  {
    path: '/admin',
    element: <RequireArea area="admin" />,
    children: [
      { index: true, element: <Navigate to="roles" replace /> },
      { path: 'roles', element: <RoleMatrixPage item={admin('/admin/roles')} /> },
    ],
  },
  { path: '*', element: <NotFound /> },
]);
