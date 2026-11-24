# Role matrix

Every account has exactly one role. What a role may do is stored in the `role_permissions` table
(migration `V3__role_permissions.sql`), and the API reads it from there; the React app only uses
it to decide which links to show. `RoleMatrixDocTest` fails the build if the table below and the
migration disagree.

## Scopes

| Scope | Meaning |
|---|---|
| `unit` | Only cases in a care unit where the account has an **active membership** and where it is **on the case's care team** (coordinator or reviewer of the case). Unit membership alone is not enough. |
| `own_case` | Only the case the portal account is linked to (patients and caregivers). |
| `global` | Instance-wide administration. A global permission never opens case content. |

## Permissions

<!-- role-matrix:start -->
| Role | Permission | Scope |
|---|---|---|
| coordinator | case.read | unit |
| coordinator | case.create | unit |
| coordinator | document.upload | unit |
| coordinator | task.edit | unit |
| coordinator | task.assign | unit |
| coordinator | audit.read | unit |
| reviewer | case.read | unit |
| reviewer | proposal.review | unit |
| reviewer | plan.publish | unit |
| reviewer | audit.read | unit |
| patient | plan.read_published | own_case |
| caregiver | plan.read_published | own_case |
| admin | user.manage | global |
| admin | unit.manage | global |
| admin | audit.read | global |
<!-- role-matrix:end -->

What is deliberately missing:

- Coordinators cannot `proposal.review` or `plan.publish`: whoever writes a plan does not approve it.
- Reviewers cannot `case.create`, `document.upload` or `task.edit`; they correct proposals through review.
- Patients and caregivers hold nothing with `unit` scope, so no staff screen or draft is reachable.
- Administrators have no `case.read`. They manage accounts and units and read the audit trail,
  but never see case content.

## Where each check happens

| Request | Check | Denied response |
|---|---|---|
| Anything except health, OpenAPI, `GET /api/v1/system/info`, `GET /api/v1/auth/csrf`, `POST /api/v1/auth/login` | Signed in | 401 `unauthenticated` |
| `/api/v1/admin/**` | Role `admin` | 403 `forbidden` |
| `GET /api/v1/me/units` | Role `coordinator` or `reviewer` | 403 `forbidden` |
| Case endpoints (from week 3) | `CaseAuthorizationService`: permission in the matrix, then scope (unit membership + care team, or linked case) | 403 `forbidden` |
| State-changing request without a matching `X-XSRF-TOKEN` header | CSRF | 403 `csrf_token_invalid` |

A case denial returns exactly the same 403 envelope as a role denial; the reason (not a member,
not on the care team, not linked, permission missing) is logged with the request id and never
sent to the client.

## In the web app

| Area | Roles | Links shown when the account holds |
|---|---|---|
| `/staff` | coordinator, reviewer | Cases and Care plans: `case.read`; Review queue: `proposal.review` |
| `/patient` | patient, caregiver | Your next steps: `plan.read_published` |
| `/admin` | admin | Role matrix: `user.manage` |

Opening another area's URL shows "You don't have access to this page" instead of the page, and
`?next=` after sign-in is ignored when it points somewhere the account may not go. These are
conveniences; `apps/web/e2e/permissions.spec.ts` checks both the screens and that the API refuses
the same calls.

## Demo accounts

| Handle | Role | Care units |
|---|---|---|
| `maya.coordinator` | coordinator | DEMO-UNIT-01 |
| `omar.coordinator` | coordinator | DEMO-UNIT-02, DEMO-UNIT-03 |
| `okafor.reviewer` | reviewer | DEMO-UNIT-01 |
| `reyes.reviewer` | reviewer | DEMO-UNIT-02, DEMO-UNIT-03 |
| `lee.admin` | admin | — |
| `alvarez.patient` | patient | linked to DEMO-CASE-008 |
| `alvarez.caregiver` | caregiver | linked to DEMO-CASE-008 |
| `case003.patient` | patient | linked to DEMO-CASE-003 |

They share one password, `CAREBRIDGE_DEMO_PASSWORD`, which is only ever read from the
environment (see [threat-model.md](threat-model.md#demo-accounts)).
