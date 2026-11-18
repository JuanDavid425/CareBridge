# Scope and limitations

CareBridge is educational coordination software built around a fictional hospital. This page lists what it does and, just as importantly, what it will never do.

## In scope

- Synthetic patient cases moving through a discharge lifecycle.
- Upload of invented discharge text, kept with a version and a content hash.
- Care plans: tasks with owners, due dates and status.
- AI that **proposes** follow-up tasks, each linked to the exact span of source text it came from.
- Mandatory clinician review before anything becomes part of a patient-facing plan.
- A demo patient portal showing only published, approved tasks.
- Rule-based flags for administrative gaps (follow-up without a date, task without an owner, missing transport).
- An append-only audit trail of every task change.
- Optional import of synthetic FHIR fixtures.

## Out of scope — never built

- Diagnosis, triage, risk scoring or urgency assignment.
- Medication advice or dose changes. Medication-related text is shown as *needs human interpretation*, never turned into a task automatically.
- Publishing any AI output without a reviewer's decision.
- Integration with a real EHR or messaging provider.
- Storing real patient data or protected health information (PHI).

## Not claimed

- CareBridge is **not** a clinical decision support product, a certified health app or a HIPAA-compliant deployment. A real deployment would need its own privacy, security and regulatory work.
- Performance figures in this repository are test objectives measured on seeded data, not production guarantees.

## Success measures for the demo

- 0 unreviewed AI proposals published.
- 0 cross-patient or cross-unit leaks in permission tests.
- 100% of task transitions audited.
- 100% of approved facts linked to a source span.
- The "Case 008" walkthrough runs end to end in about five minutes.
