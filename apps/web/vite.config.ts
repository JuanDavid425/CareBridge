import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    // Playwright specs in e2e/ need a running API; they run with `pnpm e2e`.
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
