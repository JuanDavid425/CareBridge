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

## Tests

```bash
mvn test      # context, endpoints, OpenAPI and migration policy (no Docker needed)
mvn verify    # also *IT tests: migrations against PostgreSQL + pgvector (needs Docker)
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
