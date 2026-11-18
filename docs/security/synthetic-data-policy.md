# Synthetic data policy

CareBridge runs on invented data only. This applies to every environment: local, CI, staging and the public demo.

## Rules

1. **No real patient data.** No real names, dates of birth, record numbers, addresses, phone numbers or clinical notes, even if "anonymised".
2. **Everything is marked.** Every demo patient has `synthetic_marker = true`, and every screen shows the DEMO banner.
3. **Invented documents only.** Discharge notes are written for this project or generated from fixtures in `packages/synthetic-data`. Do not paste text copied from a real record or a published case report.
4. **No third-party PHI.** Do not upload documents received from anyone else, even for testing.
5. **Mock AI by default.** `AI_PROVIDER=mock` is the default and is what CI uses. A hosted model may only be enabled locally with synthetic documents.
6. **Keep raw text out of telemetry.** Logs, traces, metrics and CI artifacts must not contain document text or prompts. Log IDs and hashes instead.
7. **Resettable.** The demo database can be wiped and reseeded at any time; nothing in it is worth keeping.

## Naming fixtures

- Patient names come from an obviously fictional list (for example "Case 008 — Tomás Alvarez (DEMO)").
- Dates are relative to the seed date so fixtures never match a real admission.
- Identifiers use a `DEMO-` prefix.

## If real data shows up

1. Delete it from the database, object storage and any logs.
2. If it was committed, remove it from git history and rotate anything that was exposed.
3. Note what happened in `docs/progress/` without repeating the data.
