"""Command line: ``python -m carebridge_synthetic generate|check``."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .check import check
from .generator import DEFAULT_SEED, generate, to_json


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="carebridge_synthetic")
    sub = parser.add_subparsers(dest="command", required=True)

    gen = sub.add_parser("generate", help="write the demo seed as JSON")
    gen.add_argument("--seed", type=int, default=DEFAULT_SEED)
    gen.add_argument("--out", type=Path, help="output file (default: stdout)")

    chk = sub.add_parser("check", help="validate a seed file against the synthetic-data rules")
    chk.add_argument("path", type=Path)

    args = parser.parse_args(argv)

    if args.command == "generate":
        text = to_json(generate(args.seed))
        if args.out:
            args.out.parent.mkdir(parents=True, exist_ok=True)
            args.out.write_text(text, encoding="utf-8")
        else:
            sys.stdout.write(text)
        return 0

    doc = json.loads(args.path.read_text(encoding="utf-8"))
    problems = check(doc)
    for problem in problems:
        print(f"FAIL {problem}")
    if problems:
        return 1
    print(f"OK {args.path}: {len(doc['careUnits'])} units, {len(doc['users'])} users, "
          f"{len(doc['cases'])} cases")
    return 0


if __name__ == "__main__":
    sys.exit(main())
