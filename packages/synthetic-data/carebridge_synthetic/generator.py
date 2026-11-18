"""Deterministic generator for the CareBridge demo seed.

Everything produced here is invented. Dates are stored as day offsets from the
seed date, never as calendar dates, so a fixture can never line up with a real
admission. The same seed always produces byte-identical JSON.
"""

from __future__ import annotations

import json
import random

FIXTURE_VERSION = 1
DEFAULT_SEED = 20260105
CASE_COUNT = 12
EMAIL_DOMAIN = "demo.carebridge.invalid"

CASE_STATUSES = ("open", "planning", "review", "active", "closed")
STAFF_ROLES = ("coordinator", "reviewer")
ROLES = ("coordinator", "reviewer", "patient", "caregiver", "admin")

CARE_UNITS = (
    {"id": "DEMO-UNIT-01", "name": "Cardiology Step-Down (DEMO)"},
    {"id": "DEMO-UNIT-02", "name": "General Medicine (DEMO)"},
    {"id": "DEMO-UNIT-03", "name": "Orthopaedics (DEMO)"},
)

# Staff accounts. Units are memberships; the admin belongs to none and cannot
# open cases. Reviewer "Okafor" is only in unit 01, so unit 02/03 cases are
# outside his access by design (used by later permission tests).
STAFF = (
    {"id": "DEMO-USR-001", "handle": "maya.coordinator", "displayName": "Maya Lindqvist (DEMO)",
     "role": "coordinator", "unitIds": ["DEMO-UNIT-01"]},
    {"id": "DEMO-USR-002", "handle": "omar.coordinator", "displayName": "Omar Haddad (DEMO)",
     "role": "coordinator", "unitIds": ["DEMO-UNIT-02", "DEMO-UNIT-03"]},
    {"id": "DEMO-USR-003", "handle": "okafor.reviewer", "displayName": "Dr. Chidi Okafor (DEMO)",
     "role": "reviewer", "unitIds": ["DEMO-UNIT-01"]},
    {"id": "DEMO-USR-004", "handle": "reyes.reviewer", "displayName": "Dr. Ana Reyes (DEMO)",
     "role": "reviewer", "unitIds": ["DEMO-UNIT-02", "DEMO-UNIT-03"]},
    {"id": "DEMO-USR-005", "handle": "lee.admin", "displayName": "Lee Novak (DEMO)",
     "role": "admin", "unitIds": []},
)

# Obviously fictional names; every one is shown with "(DEMO)".
FIRST_NAMES = ("Iris", "Bruno", "Celia", "Dario", "Elsa", "Fabian", "Greta", "Hugo",
               "Ines", "Jonas", "Kira", "Lucio", "Marta", "Nils", "Olga", "Pavel")
LAST_NAMES = ("Quillon", "Rasko", "Sentro", "Tavel", "Umbra", "Vello", "Wrenfield",
              "Xantos", "Yarrow", "Zelner", "Ambrell", "Brisco")

# The walkthrough case from the README demo workflow.
WALKTHROUGH_CASE = 8
WALKTHROUGH_PATIENT = {"firstName": "Tomás", "lastName": "Alvarez"}


def _email(handle: str) -> str:
    return f"{handle}@{EMAIL_DOMAIN}"


def _staff_for(unit_id: str, role: str) -> str:
    for user in STAFF:
        if user["role"] == role and unit_id in user["unitIds"]:
            return user["id"]
    raise ValueError(f"no {role} for {unit_id}")


def generate(seed: int = DEFAULT_SEED) -> dict:
    """Build the full seed document for ``seed``."""
    rng = random.Random(seed)
    units = [dict(unit) for unit in CARE_UNITS]

    users = []
    for staff in STAFF:
        user = dict(staff)
        user["unitIds"] = list(staff["unitIds"])
        user["email"] = _email(staff["handle"])
        user["linkedCaseIds"] = []
        users.append(user)

    patients, cases = [], []
    used_names = set()
    for number in range(1, CASE_COUNT + 1):
        if number == WALKTHROUGH_CASE:
            first, last = WALKTHROUGH_PATIENT["firstName"], WALKTHROUGH_PATIENT["lastName"]
            unit_id, status, discharge = "DEMO-UNIT-01", "open", 2
        else:
            while True:
                first, last = rng.choice(FIRST_NAMES), rng.choice(LAST_NAMES)
                if (first, last) not in used_names:
                    break
            unit_id = units[(number - 1) % len(units)]["id"]
            status = rng.choice(CASE_STATUSES)
            # Closed cases were discharged in the past, open ones are upcoming.
            discharge = rng.randint(-21, -3) if status in ("active", "closed") else rng.randint(0, 10)
        used_names.add((first, last))

        patient_id = f"DEMO-PT-{number:03d}"
        case_id = f"DEMO-CASE-{number:03d}"
        patients.append({
            "id": patient_id,
            "displayName": f"{first} {last} (DEMO)",
            "ageBand": rng.choice(("40-49", "50-59", "60-69", "70-79", "80+")),
            "syntheticMarker": True,
        })
        cases.append({
            "id": case_id,
            "label": f"Case {number:03d}",
            "patientId": patient_id,
            "unitId": unit_id,
            "status": status,
            "coordinatorId": _staff_for(unit_id, "coordinator"),
            "reviewerId": _staff_for(unit_id, "reviewer"),
            "dischargeOffsetDays": discharge,
            "syntheticMarker": True,
        })

    # Demo portal accounts: the walkthrough patient and a caregiver for the
    # same case, plus one more patient so isolation between patients can be shown.
    portal = (
        ("DEMO-USR-101", "alvarez.patient", "Tomás Alvarez (DEMO)", "patient", WALKTHROUGH_CASE),
        ("DEMO-USR-102", "alvarez.caregiver", "Lucía Alvarez (DEMO)", "caregiver", WALKTHROUGH_CASE),
        ("DEMO-USR-103", "case003.patient", patients[2]["displayName"], "patient", 3),
    )
    for user_id, handle, name, role, case_number in portal:
        users.append({
            "id": user_id,
            "handle": handle,
            "displayName": name,
            "role": role,
            "unitIds": [],
            "email": _email(handle),
            "linkedCaseIds": [f"DEMO-CASE-{case_number:03d}"],
        })

    return {
        "fixtureVersion": FIXTURE_VERSION,
        "seed": seed,
        "dataMode": "SYNTHETIC_ONLY",
        "careUnits": units,
        "users": users,
        "patients": patients,
        "cases": cases,
        "demoLogin": {
            "method": "account-picker",
            "note": "Local and demo environments only. Pick a seeded account; no passwords are stored in fixtures.",
        },
    }


def to_json(document: dict) -> str:
    return json.dumps(document, indent=2, ensure_ascii=False, sort_keys=True) + "\n"
