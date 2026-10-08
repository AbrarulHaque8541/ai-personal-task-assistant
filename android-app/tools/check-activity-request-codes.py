#!/usr/bin/env python3
"""Guard: every Activity result request code is declared once and used by name.

A duplicated request code silently routes one picker result into another feature's handler - for
example a document-picker result being consumed as a portable-backup export, or an attachment
result being treated as an update-save result. This makes the single-source-of-truth invariant
machine-verifiable instead of relying on review.
"""
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
main = root / "app/src/main/java/com/cue/daymark"
activity = (main / "MainActivity.java").read_text(encoding="utf-8")
router = (main / "updater/UpdaterActivityResultRouter.java").read_text(encoding="utf-8")

router_match = re.search(r"static final int REQUEST_CODE\s*=\s*(\d+);", router)
assert router_match, "the updater router must declare its request code"
router_value = int(router_match.group(1))

declared = {}
for name, raw in re.findall(r"static final int (REQUEST_[A-Z0-9_]+)\s*=\s*([^;]+);", activity):
    declared[name] = raw.strip()

resolved = {}
for name, raw in declared.items():
    if raw.isdigit():
        resolved[name] = int(raw)
    elif raw == "UpdaterActivityResultRouter.REQUEST_CODE":
        resolved[name] = router_value
    else:
        raise AssertionError(
            f"{name} must be a literal or the updater router's REQUEST_CODE, found {raw!r}")

buckets = {}
for name, value in resolved.items():
    buckets.setdefault(value, []).append(name)
collisions = {value: names for value, names in buckets.items() if len(names) > 1}
assert not collisions, f"duplicate Activity request codes would misroute picker results: {collisions}"

launches = re.findall(r"startActivityForResult\(([^;]*?)\);", activity)
assert launches, "expected the attachment/backup/update picker launches"
used = set()
for launch in launches:
    argument = launch.rsplit(",", 1)[-1].strip()
    assert re.fullmatch(r"REQUEST_[A-Z0-9_]+", argument), \
        f"startActivityForResult must pass a declared request-code constant, found {argument!r}"
    assert argument in resolved, f"undeclared request code constant: {argument}"
    used.add(argument)
assert used == set(resolved), \
    f"declared but unused request codes: {sorted(set(resolved) - used)}"

handler = activity.split("protected void onActivityResult", 1)[1].split(
    "private void cleanStalePickerResult", 1)[0]
assert not re.search(r"requestCode\s*==\s*\d", handler), \
    "onActivityResult must compare declared constants, not raw literals"
assert "UpdaterActivityResultRouter.route(" in handler, \
    "the updater result route must stay behind the shared router"

print(f"PASS activity request-code policy: {len(resolved)} unique codes "
      f"({', '.join(f'{n}={v}' for n, v in sorted(resolved.items()))}); "
      f"updater router code {router_value} distinct; no raw literals")
