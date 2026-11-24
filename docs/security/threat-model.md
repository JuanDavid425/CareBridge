# Threat model — identity, roles and case access

Scope: sign-in, sessions, the role matrix and case-assignment checks as built in week 2. Later
features (documents, AI proposals, publishing) get their own sections when they exist.

CareBridge only ever holds synthetic data ([no-real-phi.md](no-real-phi.md)). The model still
treats case content as if it were sensitive, because the point of the project is to show how a
real care-coordination tool would have to behave.

## What we protect

| Asset | Why it matters |
|---|---|
| Case content (notes, tasks, drafts) | Would be health information in a real deployment |
| Which cases and units a person belongs to | Reveals who is being treated where |
| Sessions and the demo password | Whoever holds them acts as that role |
| The role matrix and memberships | Changing them changes who sees what |

## Who we defend against

1. **Anonymous internet visitor** of a demo instance.
2. **Signed-in user acting outside their role**: a patient trying staff URLs, a coordinator
   opening another unit's case, an admin trying to read case content.
3. **Malicious website** visited by a signed-in user (CSRF, open redirect).
4. **Someone reading the repository** looking for credentials.

Out of scope for now: a compromised server or database administrator, denial of service, and the
hosting platform itself.

## Threats and controls

| # | Threat | Control | Verified by |
|---|---|---|---|
| T1 | Calling an API endpoint without signing in | Deny by default: only health, OpenAPI, system info, CSRF and login are public | `AuthFlowTest`, E2E "anonymous visitors" |
| T2 | Using another role's endpoints by calling the API directly | Role checks on the server (`SecurityConfig`); the UI hiding a link is not a control | E2E `expectForbidden` for every role, `AuthFlowTest` |
| T3 | Opening a case in a unit you do not belong to, or a case you are not assigned to | `CaseAuthorizationService`: matrix permission, then active unit membership **and** care-team place; `CaseScope` for list queries | `CaseAuthorizationServiceTest`, `CaseAccessDenialTest` |
| T4 | Admin reading case content | `global` scope never grants case access; admins have no `case.read` | `CaseAuthorizationServiceTest` |
| T5 | Learning why access was denied (unit exists, case exists) | One 403 `forbidden` envelope for every denial; reason only in server logs with the request id | `CaseAccessDenialTest` (envelope parity), E2E checks the body has no ids or role names |
| T6 | Finding out which handles exist through sign-in | Wrong handle, wrong password, inactive and password-less accounts all get the same 401 `invalid_credentials` | `AuthFlowTest`, E2E "wrong password" |
| T7 | Session fixation | Session id rotated on every sign-in | `AuthFlowTest` |
| T8 | Session theft through script | `CAREBRIDGE_SESSION` cookie is `HttpOnly`, `SameSite=Lax`; `Secure` with `CAREBRIDGE_SECURE_COOKIES=true` behind HTTPS | Configuration (`application.yml`) |
| T9 | Cross-site request forgery | Every state-changing request, sign-in included, needs `X-XSRF-TOKEN` matching the `XSRF-TOKEN` cookie | `CsrfCookieFlowTest`, `AuthFlowTest` |
| T10 | Open redirect through `?next=` after sign-in | Only same-origin paths the account may open are followed | `access.test.ts`, E2E "next link" |
| T11 | Deactivated or removed user keeps access | Account status and memberships are read on each request by `/auth/me` and by `CaseAuthorizationService`; a deactivated account's session is invalidated | `AuthFlowTest`, `CaseAuthorizationServiceTest` |
| T12 | Credentials committed to the repository | Demo password from the environment only; no hashes or passwords in fixtures (`carebridge_synthetic check`); `scripts/check_secrets.py` in CI | CI `secrets` job, `test_check_secrets.py` |
| T13 | A copied example value becomes a working demo password | `.env.example` leaves `CAREBRIDGE_DEMO_PASSWORD` empty; the API refuses to seed with an empty, short (< 12) or `change-me…` value, and never in `CAREBRIDGE_ENV=production` | `DemoPasswordPolicyTest`, `IdentityStoreIT` |
| T14 | Demo data mistaken for real patients | Seed is refused unless marked `SYNTHETIC_ONLY` with `DEMO-` ids; DEMO banner on every screen | `IdentityStoreIT`, synthetic seed tests |
| T15 | Schema drift between what was tested and what runs | Forward-only Flyway migrations, applied to an empty PostgreSQL and then re-validated on restart in CI | `MigrationPolicyTest`, `FlywayMigrationIT`, CI `e2e` job |

## Demo accounts

Audit of the demo accounts (week 2):

- The seed (`packages/synthetic-data/seed/demo-seed.json`) holds handles, display names,
  `.invalid` email addresses, roles, units and linked cases. It has no password, hash or token
  fields; the seed checker rejects any key that looks like one.
- All demo accounts share `CAREBRIDGE_DEMO_PASSWORD`, read from the environment when the API
  seeds them and stored only as a bcrypt hash. The seeder logs counts, not handles or emails.
- **Found and fixed:** `.env.example` used to ship `CAREBRIDGE_DEMO_PASSWORD=change-me-demo-only`,
  which passed the 12-character minimum, so copying the example gave every demo account a
  publicly known password. The example value is now empty and placeholder values are refused.
- Tests use fixed passwords only inside test code (`InMemoryIdentityStore`, `IdentityStoreIT`);
  CI generates a fresh random demo password for every E2E run.
- `CAREBRIDGE_DB_PASSWORD=change-me-local-only` in `.env.example` is accepted on purpose: that
  database only listens on `127.0.0.1` in Compose.

## Known gaps

| Gap | Risk | Plan |
|---|---|---|
| No sign-in rate limiting or lockout | Password guessing against demo accounts on a public instance | Add throttling per handle and per IP before any public demo |
| A role change only reaches URL-level checks after the user signs in again (the role is part of the session) | A demoted user keeps role-level access until sign-out or session timeout (30 min); case checks already read memberships fresh | Re-read the role per request or end sessions on role change, together with user management |
| Sessions live in the API process | No shared logout across instances; fine for a single demo instance | Move sessions to Redis if the API is scaled out |
| Audit trail does not exist yet | Denials are only in application logs | Audit events arrive with the case registry |
