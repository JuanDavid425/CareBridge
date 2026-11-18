# Personas

CareBridge has four roles. Every person in the demo is invented.

## Discharge coordinator — "Maya"

- Owns the follow-up plan for each patient leaving her care unit.
- Today she tracks transport, follow-up visits and family calls in email and a shared spreadsheet, and loses track of who owns what.
- **Needs:** one list of upcoming discharges, clear task owners and due dates, and a quick way to turn a long discharge note into tasks.
- **Can:** create cases, upload discharge text, edit and assign tasks, request publication.
- **Cannot:** approve AI-proposed tasks, or see cases outside her care unit.

## Clinical reviewer — "Dr. Okafor"

- A clinician who checks the plan before it reaches the patient.
- **Needs:** proposed tasks shown next to the exact source sentence, fast accept / edit / reject, and confidence that nothing unreviewed is published.
- **Can:** approve, correct or reject proposals and summaries; publish a reviewed plan version.
- **Cannot:** see cases from care units he is not a member of.

## Patient or caregiver (demo account) — "Mr. Alvarez" and his daughter

- Wants to know what happens next: which appointments, who arranges transport, who to call.
- **Needs:** a short, mobile-friendly list in plain language, with dates and a contact.
- **Can:** see the published tasks of their own case only.
- **Cannot:** see drafts, rejected suggestions, staff notes, or any other patient.

## System administrator — "Lee"

- Manages care units, users and configuration for the demo instance.
- **Needs:** user and role management, the audit trail, and a way to reset the synthetic data.
- **Can:** manage users, units and settings; read audit history.
- **Cannot:** approve care-plan content.

## Permission summary

| Action | Coordinator | Reviewer | Patient (demo) | Admin |
|---|---|---|---|---|
| View assigned cases in own unit | ✅ | ✅ | — | — |
| Create case, upload discharge text | ✅ | — | — | — |
| Edit and assign tasks | ✅ | — | — | — |
| Accept / edit / reject proposals | — | ✅ | — | — |
| Publish a reviewed plan | — | ✅ | — | — |
| View own published tasks | — | — | ✅ | — |
| Manage users and units | — | — | — | ✅ |
| Read audit trail | own unit | own unit | — | ✅ |
