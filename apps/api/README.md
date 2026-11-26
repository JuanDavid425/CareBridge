# CareBridge API

Spring Boot 3.5 (Java 21) modular monolith. Synthetic data only.

## Run

```bash
cp .env.example .env              # from the repo root; set CAREBRIDGE_DB_PASSWORD
docker compose up -d postgres redis
cd apps/api && mvn spring-boot:run
```

Or run everything in containers: `docker compose --profile api up --build`.

| URL | What |
|---|---|
| `GET /actuator/health/liveness` | Process is up |
| `GET /actuator/health/readiness` | Ready to serve: PostgreSQL and Redis reachable |
| `GET /api/v1/system/info` | Name, version, environment, `dataMode: SYNTHETIC_ONLY`, AI provider |
| `/swagger-ui.html`, `/v3/api-docs` | OpenAPI |

Health responses never include component details, and only the health endpoint is exposed.
Everything else requires a signed-in session.

## Sign-in and roles

Session cookie (`CAREBRIDGE_SESSION`, `HttpOnly`, `SameSite=Lax`, 30 min idle timeout) with
CSRF protection on every `POST`, sign-in included:

| Endpoint | Who | What |
|---|---|---|
| `GET /api/v1/auth/csrf` | anyone | Sets the `XSRF-TOKEN` cookie; send it back as `X-XSRF-TOKEN` |
| `POST /api/v1/auth/login` | anyone | `{"handle", "password"}`; rotates the session id, returns the account |
| `GET /api/v1/auth/me` | signed in | Account, role, active care units and permissions, read fresh |
| `POST /api/v1/auth/logout` | signed in | Ends the session (`204`) |
| `GET /api/v1/me/units` | coordinator, reviewer | Active care-unit memberships (unit switcher) |
| `GET /api/v1/admin/role-matrix` | admin | Every role's permissions and scope |

A wrong handle, wrong password or deactivated account all get the same `401
invalid_credentials`. A deactivated account's existing session ends on its next request.

Schema (`V2`, `V3`): `care_units`, `users` (one role each, bcrypt hash or none),
`team_memberships` (only coordinators and reviewers; a composite foreign key keeps the
membership role equal to the user's role) and `role_permissions`, the role matrix with a
`unit`, `own_case` or `global` scope. The matrix ships with the schema; demo accounts do not.

### Case access

Role checks above only say what a role may do. Which cases it may do it to is decided by
`CaseAuthorizationService` (`assignment` package), read fresh on every check:

- `unit` permissions (coordinators, reviewers): an active membership in the case's care unit
  **and** a place on the case's care team. Unit membership alone is not enough.
- `own_case` permissions (patients, caregivers): the account must be linked to the case.
- `global` permissions (administrators) never open case content.

`scopeFor(account, permission)` returns the same rule as a `CaseScope` (care units + account
id) for repository queries, so case lists and single-case checks cannot disagree. A refused
check is a `403 forbidden` with exactly the same body as a role check; the reason
(`OUTSIDE_UNIT`, `NOT_ASSIGNED`, `NOT_LINKED`, `NO_PERMISSION`) is only logged, with the request id.

### Demo accounts

With `CAREBRIDGE_DEMO_SEED=true` the API loads the care units, accounts, memberships, synthetic
patients and cases from
`packages/synthetic-data/seed/demo-seed.json` on startup (idempotent; refused when
`CAREBRIDGE_ENV=production`). The seed file has no credentials: every demo account signs in
with `CAREBRIDGE_DEMO_PASSWORD` from `.env`.

```bash
curl -c jar -s localhost:8080/api/v1/auth/csrf
curl -b jar -c jar -s -H "X-XSRF-TOKEN: $(awk '/XSRF-TOKEN/ {print $7}' jar)" \
  -H 'Content-Type: application/json' \
  -d '{"handle":"maya.coordinator","password":"<CAREBRIDGE_DEMO_PASSWORD>"}' \
  localhost:8080/api/v1/auth/login
```

## Case registry

All cases, patients and names are synthetic: references start with `DEMO-`, patient names end
with `(DEMO)` and the schema refuses rows without the synthetic marker.

| Endpoint | Who | What |
|---|---|---|
| `GET /api/v1/cases?search=&status=&due=&page=&size=` | coordinator, reviewer | Cases in the caller's units where the caller is on the care team, earliest follow-up first. `status` repeats or takes a comma list; `due` is `overdue`, `today` or `week`; `page` from 0, `size` up to 100 |
| `POST /api/v1/cases` | `case.create` in that unit | Opens a case for a new synthetic patient; the caller joins the care team, plus an optional reviewer from the same unit. 201 with `Location` and `ETag` |
| `GET /api/v1/cases/{id}` | `case.read` on the case | The case, its care team, `allowedTransitions` and `canEdit` for the caller. `ETag: "v3"`; `If-None-Match` gives 304 |
| `PATCH /api/v1/cases/{id}` | `case.update` | Discharge date, follow-up due date, summary. Needs `If-Match` |
| `POST /api/v1/cases/{id}/status` | see below | `{"status": "planning"}`. Needs `If-Match` |
| `GET /api/v1/cases/{id}/events` | `audit.read` | Append-only history; changed fields by name, never values |
| `GET /api/v1/care-units/{id}/reviewers` | `case.create` in that unit | Reviewers for the create form |

Status moves follow `open → planning → review → active → closed`, with `review → planning` for
requested changes. Coordinators (`case.update`) move cases into planning and review and close
them; reviewers (`plan.publish`) decide what leaves review. Anything else is 409
`invalid_transition`. Writes without `If-Match` get 428 `precondition_required`; a version that is
no longer current gets 412 `stale_version` with the current `ETag`, so two people editing the
same case cannot overwrite each other. Unknown ids are 404 `not_found`; cases outside the
caller's scope get the shared 403.

With the demo seed, the twelve seeded cases (`DEMO-CASE-001…012`) are reset to the seed on every
start, with dates recomputed from their day offsets; cases created in the app are left alone.

## Errors and request ids

Errors use one envelope: `{"error": {"code", "message", "requestId", "fields"?}}`, with stable
codes such as `unauthenticated` (401), `invalid_credentials` (401), `forbidden` (403),
`csrf_token_invalid` (403), `not_found` (404), `invalid_transition` / `case_closed` (409),
`stale_version` (412), `precondition_required` (428), `invalid_parameter` (400) and
`validation_failed` (422). Every response has an `X-Request-Id`
header: a well-formed incoming one (8–64 of `A-Z a-z 0-9 . _ -`) is kept, otherwise a UUID is
generated. The id is in each log line and in the error body.

## Tests

```bash
mvn test      # context, endpoints, sign-in/roles, OpenAPI, migration policy (no Docker needed)
mvn verify    # also *IT tests against PostgreSQL + pgvector (needs Docker): migrations,
              # identity and case constraints, the JDBC stores and the demo seed import
```

Without Docker, `IdentityStoreIT` and `CaseStoreIT` can use an existing empty database instead:
set `CAREBRIDGE_IT_DB_URL` (plus `CAREBRIDGE_IT_DB_USER`, `CAREBRIDGE_IT_DB_PASSWORD`, and
`CAREBRIDGE_IT_FLYWAY_LOCATIONS` if the server has no pgvector and needs a copy of the
migrations without that extension). Without either they are skipped.

## Migrations

Flyway, in `src/main/resources/db/migration`. The policy is **forward-only**:

- Files are `V<n>__<lower_snake_description>.sql`, numbered without gaps.
- No undo (`U`) or repeatable (`R`) scripts, and no `DROP TABLE`/`DROP SCHEMA`/`TRUNCATE`.
- A merged migration is never edited. To change something, add a new migration.
- To roll back a release, deploy the previous app version; the schema must stay compatible
  with it (expand first, contract in a later release).
- `flyway clean` is disabled in every environment. The demo is reset by recreating the
  database volume and reseeding.

`MigrationPolicyTest` checks the naming and SQL rules; `FlywayMigrationIT` applies all
migrations to an empty database and checks they are idempotent.
