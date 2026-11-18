# Week 1 — Research, design & local bootstrap

## Done

- Personas and permission summary, scope and limitations, workflow and lifecycle diagrams.
- Synthetic data policy and the no-real-PHI notice.
- React + TypeScript web app with separate staff and patient route trees, DEMO banner.
- Spring Boot API: health probes, `/api/v1/system/info`, OpenAPI, Flyway with a forward-only
  migration policy, Docker Compose for PostgreSQL + pgvector and Redis.
- CI for web typecheck/test/build, API `mvn verify` and synthetic seed checks.
- ADR 0001: modular monolith, synthetic-only data, review before publish.
- Deterministic synthetic seed: 3 care units, 8 demo accounts (every role), 12 cases including
  the Case 008 walkthrough, with a validator and tests.

## Decisions

- Demo sign-in will use an account picker in local/demo environments; fixtures hold no passwords.
- Fixture dates are day offsets from the seed date, never calendar dates.

## Next (week 2)

- Care-unit membership and role schema, Spring Security sessions, request IDs.
- React sign-in with role-aware navigation; case-assignment authorization checks.
