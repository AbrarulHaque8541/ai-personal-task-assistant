#!/usr/bin/env python3
"""Fail if the checked-out repository still contains the retired Android package/path."""
from pathlib import Path
import sys

repo = Path(sys.argv[1]).resolve()
# Build the retired identifier from character codes so the forbidden literal is
# not itself stored in this checker.
old_segment = "".join(chr(code) for code in (99, 117, 101))
old_package = ".".join(("com", old_segment, "daymark"))
old_path = old_package.replace(".", "/")
ignored = {".git", ".gradle", "build", "node_modules", "__pycache__"}
matches = []
for path in repo.rglob("*"):
    if not path.is_file() or any(part in ignored for part in path.relative_to(repo).parts):
        continue
    rel = path.relative_to(repo).as_posix()
    if old_path in rel:
        matches.append(rel)
        continue
    try:
        content = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    if old_package in content or old_path in content:
        matches.append(rel)
if matches:
    print("FAIL: retired package identity/path remains in checked-out repository:")
    for item in sorted(set(matches)):
        print(f" - {item}")
    raise SystemExit(1)
print("PASS package cleanliness: no retired package identifier or source path in working tree")
