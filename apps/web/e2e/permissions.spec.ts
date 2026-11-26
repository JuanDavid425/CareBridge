import { expect, test, type Page } from '@playwright/test';

/**
 * Negative permission checks: every account is turned away from what its role does not cover,
 * both in the UI and when it calls the API directly. Uses the demo accounts from
 * packages/synthetic-data/seed/demo-seed.json.
 */

const password = process.env.CAREBRIDGE_DEMO_PASSWORD ?? '';
const deniedHeading = 'You don’t have access to this page';

test.beforeAll(() => {
  if (password.length < 12) {
    throw new Error('Set CAREBRIDGE_DEMO_PASSWORD to the password the API seeded the demo accounts with.');
  }
});

async function signIn(page: Page, handle: string, next?: string) {
  await page.goto(next ? `/sign-in?next=${encodeURIComponent(next)}` : '/sign-in');
  await page.getByLabel('Handle').fill(handle);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();
}

async function expectDenied(page: Page, path: string, explanation: string) {
  await page.goto(path);
  await expect(page.getByRole('heading', { name: deniedHeading })).toBeVisible();
  await expect(page.getByText(explanation)).toBeVisible();
}

async function expectForbidden(page: Page, path: string) {
  const response = await page.request.get(path);
  expect(response.status(), path).toBe(403);
  const body = await response.json();
  expect(body.error.code).toBe('forbidden');
  expect(body.error.requestId).toBeTruthy();
  // The envelope says no more than "forbidden": no role names, units or case ids.
  expect(JSON.stringify(body)).not.toMatch(/DEMO-|coordinator|reviewer|admin/i);
}

const differentAccount = 'This part of CareBridge is for a different kind of account.';
const missingPermission = 'Your role does not include this page.';

test('anonymous visitors are sent to sign-in and get 401 from the API', async ({ page }) => {
  await page.goto('/admin/roles');
  await expect(page).toHaveURL(/\/sign-in\?next=%2Fadmin%2Froles/);

  const response = await page.request.get('/api/v1/admin/role-matrix');
  expect(response.status()).toBe(401);
  expect((await response.json()).error.code).toBe('unauthenticated');
});

test('a wrong password is refused without saying which part was wrong', async ({ page }) => {
  await page.goto('/sign-in');
  await page.getByLabel('Handle').fill('maya.coordinator');
  await page.getByLabel('Password').fill(`${password}-wrong`);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('alert')).toHaveText('Handle or password is incorrect.');
  await expect(page).toHaveURL(/\/sign-in/);
});

test('a coordinator cannot reach the review queue, admin pages or the patient portal', async ({ page }) => {
  await signIn(page, 'maya.coordinator');
  await expect(page).toHaveURL(/\/staff\/cases$/);

  const nav = page.getByRole('navigation', { name: 'Care team workspace' });
  await expect(nav.getByRole('link', { name: 'Cases' })).toBeVisible();
  await expect(nav.getByRole('link', { name: 'Review queue' })).toHaveCount(0);

  await expectDenied(page, '/staff/review', missingPermission);
  await expectDenied(page, '/admin/roles', differentAccount);
  await expectDenied(page, '/patient/tasks', differentAccount);
  await expectForbidden(page, '/api/v1/admin/role-matrix');
});

test('sign-in ignores a next link to a page the account may not open', async ({ page }) => {
  await signIn(page, 'maya.coordinator', '/admin/roles');
  await expect(page).toHaveURL(/\/staff\/cases$/);
});

test('a reviewer sees the review queue but not administration', async ({ page }) => {
  await signIn(page, 'okafor.reviewer');
  const nav = page.getByRole('navigation', { name: 'Care team workspace' });
  await expect(nav.getByRole('link', { name: 'Review queue' })).toBeVisible();

  await expectDenied(page, '/admin/roles', differentAccount);
  await expectForbidden(page, '/api/v1/admin/role-matrix');
});

test('a patient only gets the patient portal', async ({ page }) => {
  await signIn(page, 'alvarez.patient');
  await expect(page).toHaveURL(/\/patient\/tasks$/);
  await expect(page.getByRole('link', { name: 'Cases' })).toHaveCount(0);

  await expectDenied(page, '/staff/cases', differentAccount);
  await expectDenied(page, '/staff/review', differentAccount);
  await expectDenied(page, '/admin/roles', differentAccount);
  await expectForbidden(page, '/api/v1/me/units');
  await expectForbidden(page, '/api/v1/cases');
  await expectForbidden(page, '/api/v1/admin/role-matrix');
});

test('a caregiver is treated like the patient they support', async ({ page }) => {
  await signIn(page, 'alvarez.caregiver');
  await expectDenied(page, '/staff/cases', differentAccount);
  await expectForbidden(page, '/api/v1/me/units');
});

test('an administrator manages roles but never opens care-team pages', async ({ page }) => {
  await signIn(page, 'lee.admin');
  await expect(page).toHaveURL(/\/admin\/roles$/);
  // Positive control: the same API call that is refused for everyone else works here.
  await expect(page.getByRole('cell', { name: 'proposal.review' }).first()).toBeVisible();

  await expectDenied(page, '/staff/cases', differentAccount);
  await expectDenied(page, '/patient/tasks', differentAccount);
  await expectForbidden(page, '/api/v1/me/units');
  await expectForbidden(page, '/api/v1/cases');
});

test('signing out ends access to the API', async ({ page }) => {
  await signIn(page, 'lee.admin');
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/sign-in$/);
  const response = await page.request.get('/api/v1/admin/role-matrix');
  expect(response.status()).toBe(401);
});
