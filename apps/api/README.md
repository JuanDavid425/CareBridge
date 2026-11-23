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

### Demo accounts

With `CAREBRIDGE_DEMO_SEED=true` the API loads the care units, accounts and memberships from
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

## Errors and request ids

Errors use one envelope: `{"error": {"code", "message", "requestId", "fields"?}}`, with stable
codes such as `unauthenticated` (401), `invalid_credentials` (401), `forbidden` (403),
`csrf_token_invalid` (403) and `validation_failed` (422). Every response has an `X-Request-Id`
header: a well-formed incoming one (8–64 of `A-Z a-z 0-9 . _ -`) is kept, otherwise a UUID is
generated. The id is in each log line and in the error body.

## Tests

```bash
mvn test      # context, endpoints, sign-in/roles, OpenAPI, migration policy (no Docker needed)
mvn verify    # also *IT tests against PostgreSQL + pgvector (needs Docker): migrations,
              # identity constraints, JdbcIdentityStore and the demo seed import
```

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
