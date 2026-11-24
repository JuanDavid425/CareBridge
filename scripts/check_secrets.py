#!/usr/bin/env python3
"""Fail if tracked files contain credentials.

Demo accounts must get their password from the environment (CAREBRIDGE_DEMO_PASSWORD), never
from a file in the repository. This check runs in CI and looks for:

* password hashes or private keys committed anywhere;
* config values for password/secret/token keys that are literals instead of `${ENV}` references
  (YAML, properties, .env examples), with an empty-or-placeholder exception for example files;
* a demo password in an example file (it must stay empty: the API refuses placeholders);
* string literals assigned to password-like names in application code (tests are allowed to
  use fixed test-only passwords).

Usage: python3 scripts/check_secrets.py [ROOT]
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

HASH_OR_KEY = [
    (re.compile(r"\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}"), "bcrypt hash"),
    (re.compile(r"\{(?:bcrypt|pbkdf2|scrypt|argon2)\}[A-Za-z0-9$./+=]{20,}"), "encoded password"),
    (re.compile(r"\{noop\}[^\s\"'.)]+"), "plain-text password"),
    (re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"), "private key"),
]

SECRET_NAME = r"[A-Za-z0-9_.-]*(?:password|passwd|secret|token|api[_-]?key)[A-Za-z0-9_.-]*"
CONFIG_ASSIGNMENT = re.compile(rf"^\s*(?:export\s+)?({SECRET_NAME})\s*[:=]\s*(.*?)\s*$", re.IGNORECASE)
ENV_REFERENCE = re.compile(r"^\$\{[A-Z0-9_]+(?::[-?]?(.*))?\}$")
CODE_ASSIGNMENT = re.compile(
    rf"\b({SECRET_NAME})\s*(?:=|:)\s*[\"']([^\"'$<{{][^\"']*)[\"']", re.IGNORECASE
)

CONFIG_SUFFIXES = {".yml", ".yaml", ".properties"}
CODE_SUFFIXES = {".java", ".ts", ".tsx", ".js", ".py"}
TEST_PARTS = {"test", "tests", "e2e", "testsupport"}
# Names that hold a reference to a secret, not the secret itself.
NAME_ALLOWED = re.compile(r"(?i)(header|cookie|param|field|label|name|request|input|repository|autocomplete)")


def tracked_files(root: Path) -> list[Path]:
    try:
        out = subprocess.run(
            ["git", "-C", str(root), "ls-files", "-z"], check=True, capture_output=True
        ).stdout.decode()
        return [root / name for name in out.split("\0") if name]
    except (OSError, subprocess.CalledProcessError):
        return [p for p in root.rglob("*") if p.is_file() and ".git" not in p.parts]


def is_example(path: Path) -> bool:
    return path.name.startswith(".env") or "example" in path.name


def is_test(path: Path, root: Path) -> bool:
    rel = path.relative_to(root)
    return (
        bool(TEST_PARTS.intersection(rel.parts))
        or rel.name.startswith("test_")
        or re.search(r"\.(test|spec)\.[jt]sx?$", rel.name) is not None
    )


def config_value_problem(path: Path, name: str, value: str) -> str | None:
    value = value.strip().strip("\"'")
    if not value or NAME_ALLOWED.search(name):
        return None
    # Generated at run time (shell substitution) or injected by CI (${{ secrets.X }}).
    if "$(" in value or value.startswith("${{"):
        return None
    ref = ENV_REFERENCE.match(value)
    if ref:
        default = (ref.group(1) or "").strip()
        if default and not default.startswith("set "):
            return f"{name} has a literal default in ${{...}}"
        return None
    if is_example(path):
        if "DEMO" in name.upper():
            return f"{name} must stay empty in example files"
        if value.lower().startswith("change-me"):
            return None
    return f"{name} is set to a literal value"


def scan(root: Path) -> list[str]:
    problems: list[str] = []
    for path in tracked_files(root):
        if path.suffix in {".png", ".jpg", ".ico", ".lock"} or path.name == "check_secrets.py":
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        rel = path.relative_to(root)
        config = path.suffix in CONFIG_SUFFIXES or path.name.startswith(".env")
        code = path.suffix in CODE_SUFFIXES and not is_test(path, root)
        for number, line in enumerate(text.splitlines(), start=1):
            where = f"{rel}:{number}"
            for pattern, label in HASH_OR_KEY:
                if pattern.search(line):
                    problems.append(f"{where}: {label}")
            if config and not line.lstrip().startswith("#"):
                match = CONFIG_ASSIGNMENT.match(line)
                if match:
                    problem = config_value_problem(path, match.group(1), match.group(2))
                    if problem:
                        problems.append(f"{where}: {problem}")
            if code:
                for match in CODE_ASSIGNMENT.finditer(line):
                    if not NAME_ALLOWED.search(match.group(1)):
                        problems.append(f"{where}: {match.group(1)} is assigned a string literal")
    return problems


def main(argv: list[str]) -> int:
    root = Path(argv[1] if len(argv) > 1 else ".").resolve()
    problems = scan(root)
    for problem in problems:
        print(problem)
    print(f"check_secrets: {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
