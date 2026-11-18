# ADR 0001: Modular monolith, synthetic-only data, review before publish

- **Status:** Accepted
- **Date:** Week 1

## Context

CareBridge coordinates post-discharge follow-up: cases, discharge text, care-plan tasks, a
clinical review step and a patient portal. AI extraction is part of the product, but its output
must never reach a patient without a clinician's decision. The project is built by a small team
over eight weeks and is shown as a public demo, so it must be simple to run locally and must
never hold real patient data.

## Decision

1. **One Spring Boot application (Java 21) as a modular monolith.** Packages by capability
   (`system`, later `identity`, `cases`, `documents`, `plans`, `extraction`, `portal`, `audit`)
   with no cross-module repository access. Splitting into services is not justified at this size.
2. **React + TypeScript web app** with two separate route trees, `/staff` and `/patient`, so no
   staff screen can be linked from the patient area.
3. **PostgreSQL 17 with pgvector** as the single store; **Redis** for sessions and short-lived
   job state. Schema changes go through **forward-only Flyway migrations** (see
   `apps/api/README.md`).
4. **Synthetic data only.** Every record is invented and marked (`syntheticMarker`, `DEMO-` ids,
   "(DEMO)" names); the API reports `dataMode: SYNTHETIC_ONLY`. Seed data comes from the
   deterministic generator in `packages/synthetic-data`, validated in CI.
5. **AI proposes, people decide.** Extraction output is stored as *proposals* linked to a source
   span. Only a reviewer's accept or edit creates a plan task, and only a reviewer publishes a
   plan. The default AI provider is a `mock` with recorded synthetic responses; CI never calls a
   hosted model.
6. **Access is scoped by care-unit membership and case assignment**, enforced in the API (not
   only in the UI). Patient and caregiver accounts are linked to a single patient's cases.

## Consequences

- Local setup is `docker compose up` plus the two apps; no cloud account is needed.
- One deployable keeps transactions simple, e.g. approving a proposal and creating the task
  happen in one database transaction.
- Module boundaries are a convention and need review discipline (and later an architecture test).
- Real-world integrations (EHR, messaging, real models) stay out of scope; FHIR is limited to
  synthetic fixtures.
- Anything that looks like real data is a defect and is handled by the synthetic data policy.
