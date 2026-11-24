"""Tests for check_secrets.py: python3 -m unittest discover -s scripts -p 'test_*.py'"""

import tempfile
import unittest
from pathlib import Path

import check_secrets

# Built from pieces so this file does not trip the check itself.
BCRYPT = "$2a$10$" + "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0"
PRIVATE_KEY = "-----BEGIN OPENSSH " + "PRIVATE KEY-----"
NOOP = "{no" + "op}secret"


class CheckSecretsTest(unittest.TestCase):
    def scan(self, files: dict[str, str]) -> list[str]:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name, text in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")
            return check_secrets.scan(root)

    def test_clean_config_passes(self):
        problems = self.scan({
            "application.yml": "spring:\n  datasource:\n    password: ${DB_PASSWORD:}\n",
            ".env.example": "DB_PASSWORD=change-me-local-only\nCAREBRIDGE_DEMO_PASSWORD=\n",
            "compose.yaml": "POSTGRES_PASSWORD: ${DB_PASSWORD:?set DB_PASSWORD in .env}\n",
            "src/Main.java": 'String header = request.getHeader("X-XSRF-TOKEN");\n',
            "ci.yml": '  password="ci-$(openssl rand -hex 16)"\n  token: ${{ secrets.TOKEN }}\n',
        })
        self.assertEqual(problems, [])

    def test_demo_password_in_example_is_flagged(self):
        problems = self.scan({".env.example": "CAREBRIDGE_DEMO_PASSWORD=change-me-demo-only\n"})
        self.assertEqual(len(problems), 1)
        self.assertIn("must stay empty", problems[0])

    def test_literal_config_values_and_defaults_are_flagged(self):
        problems = self.scan({
            "application.yml": "password: hunter2hunter2\napi-key: ${KEY:abc123}\n",
            ".env": "SESSION_SECRET=real-value\n",
        })
        self.assertEqual(len(problems), 3)

    def test_hashes_and_keys_are_flagged_anywhere(self):
        problems = self.scan({
            "seed.json": f'{{"hash": "{{bcrypt}}{BCRYPT}"}}\n',
            "notes.md": PRIVATE_KEY + "\n",
            "users.sql": f"INSERT INTO users VALUES ('{NOOP}');\n",
        })
        self.assertEqual(len(problems), 4)  # bcrypt pattern + {bcrypt} prefix, key, {noop}

    def test_code_literals_are_flagged_outside_tests_only(self):
        line = 'const password = "letmein-please";\n'
        self.assertEqual(len(self.scan({"src/login.ts": line})), 1)
        self.assertEqual(self.scan({"src/login.test.ts": line, "e2e/auth.spec.ts": line}), [])


if __name__ == "__main__":
    unittest.main()
