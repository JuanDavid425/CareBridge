# Getting started

CareBridge runs locally with Docker Compose for PostgreSQL and Redis, a Spring Boot API and a
React web app. Read the [no-real-PHI notice](security/no-real-phi.md) first: only synthetic data
may be used.

## Prerequisites

| Tool | Version |
|---|---|
| Docker with Compose v2 | recent |
| Java (Temurin) + Maven | 21 + 3.9 |
| Node.js + pnpm | 22 + 10 (`corepack enable`) |
| Python | 3.11+ (synthetic data tools only) |

## 1. Configure

```bash
cp .env.example .env
# set CAREBRIDGE_DB_PASSWORD to any local value; keep AI_PROVIDER=mock
```

`.env` is ignored by git. Never put real credentials or patient data in it.

## 2. Start PostgreSQL and Redis

```bash
docker compose up -d postgres redis
docker compose ps          # both should become "healthy"
```

Ports are bound to `127.0.0.1` only (5432, 6379).

## 3. Run the API

```bash
cd apps/api
mvn spring-boot:run
```

Flyway applies the migrations on startup. Check it with:

```bash
curl -s localhost:8080/actuator/health/readiness   # {"status":"UP"}
curl -s localhost:8080/api/v1/system/info          # dataMode: SYNTHETIC_ONLY
```

OpenAPI is at <http://localhost:8080/swagger-ui.html>. Alternatively run the API in a container:
`docker compose --profile api up --build`.

## 4. Run the web app

```bash
pnpm install
pnpm dev                   # http://localhost:5173
```

Every screen shows the DEMO banner. Staff screens are under `/staff`, the patient view under
`/patient`.

## 5. Synthetic seed data

```bash
cd packages/synthetic-data
python3 -m carebridge_synthetic check seed/demo-seed.json
```

`seed/demo-seed.json` lists the demo care units, accounts and cases, including the Case 008
walkthrough. The demo accounts and what each one shows are described in
[packages/synthetic-data/README.md](../packages/synthetic-data/README.md#demo-login-options).

With `CAREBRIDGE_DEMO_SEED=true` in `.env`, the API loads the seed's care units, accounts and
memberships when it starts. Every demo account signs in with `CAREBRIDGE_DEMO_PASSWORD`; see
[the API README](../apps/api/README.md#sign-in-and-roles) for the endpoints, or sign in from
the web app at `http://localhost:5173/sign-in` (`pnpm dev`, which proxies `/api` to the API).
Each role lands in its own area: coordinators and reviewers in `/staff`, patients and
caregivers in `/patient`, administrators in `/admin`. Cases are loaded once the case registry
exists.

## Tests

```bash
pnpm typecheck && pnpm test                                   # web
cd apps/api && mvn test                                       # API, no Docker needed
cd apps/api && mvn verify                                     # + migration tests (Docker)
cd packages/synthetic-data && python3 -m unittest discover -s tests
```

CI runs all of these on every push to `main` and on pull requests.

## Reset

The demo holds nothing worth keeping:

```bash
docker compose down -v     # removes the database and Redis volumes
docker compose up -d postgres redis
```

`flyway clean` is disabled on purpose; recreating the volume is the reset.
