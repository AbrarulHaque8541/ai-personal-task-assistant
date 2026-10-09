#!/usr/bin/env python3
"""Guard Daymark's native WebView network-image preference wiring."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
for marker in (
    'BROWSER_BLOCK_IMAGES_KEY = "block_network_images"',
    "readBrowserImagesBlockedPreference()",
    "browserWebView.getSettings().setBlockNetworkImage(browserImagesBlocked);",
    'setText("Block network images (save data)")',
    "setBrowserImagesBlocked(enabled)",
    "for (DaymarkWebView tab : browserTabs)",
    "rollback.getSettings().setBlockNetworkImage(previous)",
    "Reload the current page to apply it to images already loaded.",
    "it does not block all ads or tracking",
    'text("Full-screen page", 16, palette.text, Typeface.BOLD)',
    "Close full-screen page and return to standard browser view",
):
    assert marker in activity, f"network image preference missing or misleading: {marker}"
print("PASS: opt-in WebView network-image blocking is persisted and applied to current/new tabs")
