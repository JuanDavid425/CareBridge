# Synthetic data

Deterministic demo seed for CareBridge: care units, demo accounts, synthetic patients and
discharge cases. Standard-library Python 3.11+, no dependencies. Everything here is invented;
see [the synthetic data policy](../../docs/security/synthetic-data-policy.md).

```bash
cd packages/synthetic-data
python3 -m carebridge_synthetic generate --out seed/demo-seed.json   # default seed 20260105
python3 -m carebridge_synthetic check seed/demo-seed.json
python3 -m unittest discover -s tests
```

`seed/demo-seed.json` is committed and must match the generator output (a test checks this).
After changing the generator, regenerate it and bump `FIXTURE_VERSION` if the shape changes.

## Contents

| Section | What |
|---|---|
| `careUnits` | 3 units: Cardiology Step-Down, General Medicine, Orthopaedics |
| `users` | 2 coordinators, 2 reviewers, 1 admin, 2 patient accounts and 1 caregiver |
| `patients` | 12 synthetic patients, `syntheticMarker: true`, name ending in "(DEMO)", age band only |
| `cases` | 12 discharge cases across all lifecycle states, each with a coordinator and reviewer from its unit |

Case 008 (Tomás Alvarez, Cardiology Step-Down, status `open`) is fixed for every seed: it is the
README walkthrough case. Discharge dates are `dischargeOffsetDays` relative to the seed date.

## Demo login options

Demo environments use an account picker (`demoLogin.method: account-picker`): no passwords are
stored in fixtures. Accounts use the reserved `demo.carebridge.invalid` domain.

| Account | Role | Units / cases | Shows |
|---|---|---|---|
| `maya.coordinator` | Coordinator | Unit 01 | Case 008 walkthrough, creating and assigning tasks |
| `omar.coordinator` | Coordinator | Units 02, 03 | A second coordinator's separate case list |
| `okafor.reviewer` | Reviewer | Unit 01 | Review queue; cannot open unit 02/03 cases |
| `reyes.reviewer` | Reviewer | Units 02, 03 | Review queue for the other units |
| `lee.admin` | Admin | — | Users, units, audit trail; no case content |
| `alvarez.patient` | Patient | Case 008 | Published tasks of Case 008 only |
| `alvarez.caregiver` | Caregiver | Case 008 | Same view as the patient it is linked to |
| `case003.patient` | Patient | Case 003 | A second patient, to show isolation between patients |

## Rules enforced by `check`

- `dataMode` is `SYNTHETIC_ONLY`; every id starts with `DEMO-`; ids are unique.
- No calendar dates, no password/secret/token fields, no identifying fields (date of birth,
  record number, address, phone).
- Patients and cases carry `syntheticMarker: true`; names end in "(DEMO)".
- Each case's coordinator and reviewer have that role and belong to the case's unit.
- Only coordinators and reviewers belong to units; portal accounts are linked to cases of a
  single patient.
- Every role has at least one demo account, and Case 008 exists.
