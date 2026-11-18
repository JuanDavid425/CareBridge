# No real PHI

> **CareBridge must never contain real patient information.** It is educational software that
> runs on invented data only. It is not a clinical system, not HIPAA compliant and not
> certified for any medical use.

This notice applies to every environment — local machines, CI, staging and the public demo —
and to every contributor.

## Do not

- Enter, upload, paste or import any real person's health information: names, dates of birth,
  record numbers, addresses, phone numbers, insurance details, clinical notes, discharge
  letters or images.
- Use "anonymised" or "de-identified" real records. They are still out of scope.
- Connect CareBridge to a real EHR, FHIR server, messaging provider or patient portal.
- Send real documents to a hosted AI model through CareBridge.
- Use CareBridge output to make or support any care decision.

## Do

- Use the seeded demo data (`packages/synthetic-data`) or documents written for this project.
- Keep `AI_PROVIDER=mock` unless you are testing locally with synthetic documents.
- Report anything that looks real by following "If real data shows up" in the
  [synthetic data policy](synthetic-data-policy.md).

## Where the notice is shown

- The README status line and this page.
- The DEMO banner on every screen of the web app.
- `GET /api/v1/system/info` returns `dataMode: SYNTHETIC_ONLY`.
- Seed data: `dataMode: SYNTHETIC_ONLY` and `syntheticMarker: true` on every patient and case,
  checked by `python3 -m carebridge_synthetic check`.
