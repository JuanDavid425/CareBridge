# Week 2 — Identity, roles & assignment policy

## Done

- Schema for care units, accounts and care-unit memberships (a membership's role must match its
  account's role) and the `role_permissions` matrix with `unit`, `own_case` and `global` scopes.
- Spring Security session sign-in with JSON endpoints, session-id rotation, cookie CSRF for the
  React app, one JSON error envelope for 401/403/422, and `X-Request-Id` on every response.
- Optional demo seeding of care units, accounts and memberships from the synthetic seed.
- React sign-in, separate staff / patient / admin areas, links filtered by the role matrix,
  denied states, and an admin role-matrix page.
- `CaseAuthorizationService` and `CaseScope`: unit scope needs an active membership **and** a
  place on the case's care team; patients only reach their linked case; admins never reach case
  content. Every denial gives the same 403 envelope.
- [Threat model](../security/threat-model.md) and [role matrix](../security/role-matrix.md);
  `RoleMatrixDocTest` keeps the documented matrix equal to the migration.
- Negative-permission Playwright tests for every demo role (screens and direct API calls).
- CI: secret check, and a job that applies all migrations to an empty PostgreSQL, restarts on the
  migrated schema, then runs the end-to-end tests.

## Fixed

- `.env.example` shipped a working demo password (`change-me-demo-only`). It is now empty, and
  the API refuses to seed with an empty, short or `change-me…` password.
- The CSRF cookie test depended on test-class order (the `csrf()` test helper replaces the
  token repository of the shared Spring context). It now runs in its own context and does the
  browser's real cookie/header round trip.
- Signing out sometimes landed on `/sign-in?next=<previous page>`, so the next person to sign in
  on that browser was sent to the previous account's page. Sign-out now always goes to a plain
  sign-in page (found by the end-to-end tests).

## Known gaps

No sign-in throttling yet, role changes reach URL-level checks only after signing in again, and
there is no audit trail until the case registry exists. See the
[threat model](../security/threat-model.md#known-gaps).

## Next (week 3)

- Synthetic patients and discharge cases with versioned updates, case list and detail screens.
- Versioned source documents with content hashes, upload and side-by-side preview.
