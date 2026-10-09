#!/usr/bin/env python3
"""Guard critical UX and extension-import trust wiring until device tests exist."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")

import_flow = activity.split("private void importExtensionFromUri", 1)[1].split(
    "private void confirmAndInstallExtension", 1
)[0]
assert "confirmAndInstallExtension(ext);" in import_flow, "all imported packs must pass through explicit review"
assert "installUserPack(ext)" not in import_flow, "import must not persist code before confirmation"

review = activity.split("private void confirmAndInstallExtension", 1)[1].split(
    "private void showBrowserExtensionsManager", 1
)[0]
for marker in (
    'setTitle("Review before adding")',
    'setNegativeButton("Cancel"',
    'setPositiveButton("Trust & add"',
    "TRUST WARNING",
    "matching website page content",
    "send it to an external service",
    "none (does not run on pages)",
    "installUserPack(ext)",
):
    assert marker in review, f"extension trust review missing marker: {marker}"
assert review.index('setPositiveButton("Trust & add"') < review.index("installUserPack(ext)"), \
    "persist imported extension only from the explicit positive action"

settings = activity.split("private void showSettingsDialog()", 1)[1].split(
    "private void addSettingsSection", 1
)[0]
for marker in (
    "PERSONALIZE",
    "ACCESSIBILITY & DATA",
    "POWER FEATURES",
    "addSettingsRow(content",
    "content.animate().alpha(1f).translationY(0f).setDuration(160L).start()",
):
    assert marker in settings, f"modern More settings missing marker: {marker}"
assert '.setItems(options' not in settings, "More must not regress to a flat legacy item list"

print("PASS: imported extension trust gate and modern More settings wiring")
