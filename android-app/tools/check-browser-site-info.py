#!/usr/bin/env python3
"""Source regression checks for the browser's bounded Site information panel (#135)."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
menu = activity.split("private void showBrowserOverflowMenu", 1)[1].split(
    "private void importExtensionFromUri", 1
)[0]
site = activity.split("private void showBrowserSiteInfoDialog()", 1)[1].split(
    "private void toggleDesktopSite()", 1
)[0]

assert '"Site info"' in menu and "showBrowserSiteInfoDialog()" in menu, \
    "the Site info action must be discoverable from browser actions"
assert "case 10:" in menu and "setBrowserOnlineEnabled(false)" in menu, \
    "adding Site info must preserve the Turn off online browsing action"
assert "BrowserAddress.isAllowedWebUrl(url)" in site, \
    "site information must only be shown for a validated HTTPS URL"
assert "target.getCertificate()" in site, \
    "show certificate metadata only when Android WebView provides it"
assert "getIssuedTo()" in site and "getIssuedBy()" in site, \
    "certificate subject and issuer should be presented when available"
assert "does not bypass SSL certificate errors" in site, \
    "the panel must accurately disclose the browser's certificate-error policy"
assert "HTTPS does not guarantee that page content is safe" in site, \
    "the panel must not imply that HTTPS means the page is trustworthy"
assert "not a phishing, reputation, or third-party request audit" in site, \
    "the panel must not imply a complete security assessment"
assert "Open an HTTPS page before viewing site information." in site, \
    "no-page state must give the user a useful explanation"
print("PASS browser Site info: HTTPS-only scope, available certificate metadata, and honest safety limitations")

# Work by: ChatGPT
# Model: GPT-6
# Tooling: GitHub MCP tools
# Timestamp (UTC): 2026-10-09T17:35:19.823Z
