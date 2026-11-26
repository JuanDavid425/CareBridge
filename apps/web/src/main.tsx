import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
// The DOM build wires ReactDOM.flushSync into router updates (used by the case filters).
import { RouterProvider } from 'react-router/dom';
import { AuthProvider } from './auth/AuthProvider';
import { router } from './router';
import './styles.css';

const root = document.getElementById('root');
if (!root) throw new Error('Root element #root not found');

createRoot(root).render(
  <StrictMode>
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  </StrictMode>,
);
