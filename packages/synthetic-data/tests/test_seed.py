import copy
import json
import unittest
from pathlib import Path

from carebridge_synthetic import check, generate
from carebridge_synthetic.generator import to_json

SEED_FILE = Path(__file__).resolve().parent.parent / "seed" / "demo-seed.json"


def find(items, item_id):
    return next(item for item in items if item["id"] == item_id)


class GeneratorTest(unittest.TestCase):
    def test_same_seed_gives_identical_output(self):
        self.assertEqual(to_json(generate(7)), to_json(generate(7)))

    def test_different_seed_changes_cases_but_not_walkthrough(self):
        a, b = generate(1), generate(2)
        self.assertNotEqual(a["cases"], b["cases"])
        self.assertEqual(find(a["cases"], "DEMO-CASE-008"), find(b["cases"], "DEMO-CASE-008"))

    def test_committed_seed_is_up_to_date(self):
        self.assertEqual(SEED_FILE.read_text(encoding="utf-8"), to_json(generate()))

    def test_generated_seeds_pass_check(self):
        for seed in range(50):
            with self.subTest(seed=seed):
                self.assertEqual(check(generate(seed)), [])

    def test_walkthrough_case_is_open_in_unit_01(self):
        doc = generate()
        case = find(doc["cases"], "DEMO-CASE-008")
        self.assertEqual(case["status"], "open")
        self.assertEqual(case["unitId"], "DEMO-UNIT-01")
        self.assertEqual(find(doc["patients"], case["patientId"])["displayName"], "Tomás Alvarez (DEMO)")

    def test_every_role_has_a_demo_login(self):
        roles = {user["role"] for user in generate()["users"]}
        self.assertEqual(roles, {"coordinator", "reviewer", "patient", "caregiver", "admin"})

    def test_reviewer_okafor_cannot_reach_other_units(self):
        doc = generate()
        okafor = find(doc["users"], "DEMO-USR-003")
        reachable = {c["id"] for c in doc["cases"] if c["unitId"] in okafor["unitIds"]}
        self.assertIn("DEMO-CASE-008", reachable)
        self.assertLess(len(reachable), len(doc["cases"]))


class CheckTest(unittest.TestCase):
    def setUp(self):
        self.doc = generate()

    def assertRejected(self, doc, fragment):
        problems = check(doc)
        self.assertTrue(any(fragment in p for p in problems), problems)

    def test_missing_synthetic_marker(self):
        self.doc["patients"][0]["syntheticMarker"] = False
        self.assertRejected(self.doc, "syntheticMarker must be true")

    def test_real_looking_email(self):
        self.doc["users"][0]["email"] = "maya@example-hospital.org"
        self.assertRejected(self.doc, "email must use")

    def test_password_field(self):
        self.doc["users"][0]["password"] = "demo"
        self.assertRejected(self.doc, "password fields are not allowed")

    def test_calendar_date(self):
        self.doc["cases"][0]["note"] = "discharged 2025-03-14"
        self.assertRejected(self.doc, "calendar dates are not allowed")

    def test_identifying_field(self):
        self.doc["patients"][0]["dateOfBirth"] = 1950
        self.assertRejected(self.doc, "identifying field")

    def test_id_without_demo_prefix(self):
        self.doc["cases"][0]["id"] = "CASE-001"
        self.assertRejected(self.doc, "must start with DEMO-")

    def test_duplicate_id(self):
        self.doc["patients"][1]["id"] = self.doc["patients"][0]["id"]
        self.assertRejected(self.doc, "duplicate id")

    def test_unknown_status(self):
        self.doc["cases"][0]["status"] = "triaged"
        self.assertRejected(self.doc, "unknown status")

    def test_cross_unit_reviewer(self):
        case = find(self.doc["cases"], "DEMO-CASE-002")
        self.assertNotEqual(case["unitId"], "DEMO-UNIT-01")
        case["reviewerId"] = "DEMO-USR-003"
        self.assertRejected(self.doc, "is not a member of")

    def test_coordinator_slot_needs_coordinator(self):
        find(self.doc["cases"], "DEMO-CASE-008")["coordinatorId"] = "DEMO-USR-003"
        self.assertRejected(self.doc, "coordinatorId must reference a coordinator")

    def test_portal_account_linked_to_two_patients(self):
        find(self.doc["users"], "DEMO-USR-101")["linkedCaseIds"].append("DEMO-CASE-001")
        self.assertRejected(self.doc, "more than one patient")

    def test_admin_cannot_be_in_unit(self):
        find(self.doc["users"], "DEMO-USR-005")["unitIds"] = ["DEMO-UNIT-01"]
        self.assertRejected(self.doc, "only coordinators and reviewers")

    def test_missing_role_login(self):
        self.doc["users"] = [u for u in self.doc["users"] if u["role"] != "caregiver"]
        self.assertRejected(self.doc, "no demo login for role caregiver")

    def test_missing_walkthrough_case(self):
        self.doc["cases"] = [c for c in self.doc["cases"] if c["id"] != "DEMO-CASE-008"]
        self.doc["users"] = [u for u in self.doc["users"] if "DEMO-CASE-008" not in u["linkedCaseIds"]]
        self.assertRejected(self.doc, "DEMO-CASE-008 is missing")

    def test_check_does_not_mutate_input(self):
        before = copy.deepcopy(self.doc)
        check(self.doc)
        self.assertEqual(self.doc, before)

    def test_committed_seed_passes(self):
        self.assertEqual(check(json.loads(SEED_FILE.read_text(encoding="utf-8"))), [])


if __name__ == "__main__":
    unittest.main()
