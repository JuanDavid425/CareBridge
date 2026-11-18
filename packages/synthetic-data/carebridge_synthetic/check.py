"""Validation rules for a CareBridge seed document.

``check`` returns a list of problems; an empty list means the seed is safe to
load. The rules follow docs/security/synthetic-data-policy.md and the
permission summary in docs/product/personas.md.
"""

from __future__ import annotations

import re

from .generator import CASE_STATUSES, EMAIL_DOMAIN, ROLES, STAFF_ROLES

ISO_DATE = re.compile(r"\b\d{4}-\d{2}-\d{2}\b")
SECRET_KEYS = ("password", "secret", "token", "hash", "credential")
FORBIDDEN_KEYS = ("dateOfBirth", "dob", "mrn", "ssn", "address", "phone")


def _walk(value, path="$"):
    if isinstance(value, dict):
        for key, child in value.items():
            yield path, key, child
            yield from _walk(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from _walk(child, f"{path}[{index}]")


def check(doc: dict) -> list[str]:
    problems: list[str] = []

    if doc.get("dataMode") != "SYNTHETIC_ONLY":
        problems.append("dataMode must be SYNTHETIC_ONLY")

    # Field-level rules for the whole document.
    for path, key, child in _walk(doc):
        lowered = key.lower()
        if any(word in lowered for word in SECRET_KEYS):
            problems.append(f"{path}.{key}: secrets or password fields are not allowed")
        if key in FORBIDDEN_KEYS:
            problems.append(f"{path}.{key}: identifying field is not allowed")
        if isinstance(child, str) and ISO_DATE.search(child):
            problems.append(f"{path}.{key}: calendar dates are not allowed, use day offsets")

    ids: set[str] = set()
    for section in ("careUnits", "users", "patients", "cases"):
        for item in doc.get(section, []):
            item_id = item.get("id", "")
            if not item_id.startswith("DEMO-"):
                problems.append(f"{section}: id {item_id!r} must start with DEMO-")
            if item_id in ids:
                problems.append(f"{section}: duplicate id {item_id}")
            ids.add(item_id)

    units = {u["id"] for u in doc.get("careUnits", [])}
    for unit in doc.get("careUnits", []):
        if not unit.get("name", "").endswith("(DEMO)"):
            problems.append(f"{unit['id']}: unit name must end with (DEMO)")

    users = {u["id"]: u for u in doc.get("users", [])}
    for user in users.values():
        uid = user["id"]
        if user.get("role") not in ROLES:
            problems.append(f"{uid}: unknown role {user.get('role')!r}")
        if not user.get("email", "").endswith("@" + EMAIL_DOMAIN):
            problems.append(f"{uid}: email must use {EMAIL_DOMAIN}")
        if not user.get("displayName", "").endswith("(DEMO)"):
            problems.append(f"{uid}: display name must end with (DEMO)")
        for unit_id in user.get("unitIds", []):
            if unit_id not in units:
                problems.append(f"{uid}: unknown unit {unit_id}")
        if user.get("role") in STAFF_ROLES and not user.get("unitIds"):
            problems.append(f"{uid}: staff user needs at least one care unit")
        if user.get("role") not in STAFF_ROLES and user.get("unitIds"):
            problems.append(f"{uid}: only coordinators and reviewers belong to care units")

    present_roles = {u.get("role") for u in users.values()}
    for role in ROLES:
        if role not in present_roles:
            problems.append(f"no demo login for role {role}")

    patients = {p["id"]: p for p in doc.get("patients", [])}
    for patient in patients.values():
        if patient.get("syntheticMarker") is not True:
            problems.append(f"{patient['id']}: syntheticMarker must be true")
        if not patient.get("displayName", "").endswith("(DEMO)"):
            problems.append(f"{patient['id']}: display name must end with (DEMO)")

    cases = {c["id"]: c for c in doc.get("cases", [])}
    for case in cases.values():
        cid, unit_id = case["id"], case.get("unitId")
        if case.get("syntheticMarker") is not True:
            problems.append(f"{cid}: syntheticMarker must be true")
        if case.get("status") not in CASE_STATUSES:
            problems.append(f"{cid}: unknown status {case.get('status')!r}")
        if case.get("patientId") not in patients:
            problems.append(f"{cid}: unknown patient {case.get('patientId')}")
        if unit_id not in units:
            problems.append(f"{cid}: unknown unit {unit_id}")
        if not isinstance(case.get("dischargeOffsetDays"), int):
            problems.append(f"{cid}: dischargeOffsetDays must be an integer")
        for field, role in (("coordinatorId", "coordinator"), ("reviewerId", "reviewer")):
            staff = users.get(case.get(field))
            if staff is None or staff.get("role") != role:
                problems.append(f"{cid}: {field} must reference a {role}")
            elif unit_id not in staff.get("unitIds", []):
                problems.append(f"{cid}: {field} {staff['id']} is not a member of {unit_id}")

    if "DEMO-CASE-008" not in cases:
        problems.append("walkthrough case DEMO-CASE-008 is missing")

    # Portal accounts see exactly the cases they are linked to, and only for one patient.
    for user in users.values():
        linked = user.get("linkedCaseIds", [])
        if user.get("role") in ("patient", "caregiver"):
            if not linked:
                problems.append(f"{user['id']}: portal account must be linked to a case")
            linked_patients = {cases[c]["patientId"] for c in linked if c in cases}
            if len(linked_patients) > 1:
                problems.append(f"{user['id']}: portal account linked to more than one patient")
            for case_id in linked:
                if case_id not in cases:
                    problems.append(f"{user['id']}: unknown linked case {case_id}")
        elif linked:
            problems.append(f"{user['id']}: only portal accounts have linked cases")

    return problems
