import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests run against a real API with the synthetic demo seed loaded
 * (CAREBRIDGE_DEMO_SEED=true) and sign in with CAREBRIDGE_DEMO_PASSWORD.
 * The web app is started here unless CAREBRIDGE_WEB_URL points at a running one.
 */
const externalWeb = process.env.CAREBRIDGE_WEB_URL;
const baseURL = externalWeb ?? 'http://localhost:5173';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: externalWeb
    ? undefined
    : {
        command: 'pnpm dev',
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 60_000,
      },
});
