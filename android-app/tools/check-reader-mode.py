#!/usr/bin/env python3
"""Guard user-triggered, local-only Reader Mode and separate full-screen navigation."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
reader = activity.split("private void openReaderMode()", 1)[1].split(
    "private void showReaderModeDialog", 1
)[0]
dialog = activity.split("private void showReaderModeDialog", 1)[1].split(
    "private void openFullScreenWebView", 1
)[0]
for marker in (
    'compactButton("Reader mode", true)',
    "browserReaderButton.setOnClickListener(view -> openReaderMode())",
    "browserReaderActionRow.setVisibility(available && hasPage ? View.VISIBLE : View.GONE)",
    "browserExpandButton.setVisibility(available && hasPage ? View.VISIBLE : View.GONE)",
    "private void openFullScreenWebView()",
):
    assert marker in activity, f"Reader Mode/full-screen UI wiring missing: {marker}"
for marker in (
    "browserNetworkPolicy.allowsRemoteLoads()",
    "BrowserAddress.isAllowedWebUrl(browserWebView.getUrl())",
    "browserWebView.getProgress() < 100",
    "source.evaluateJavascript(script",
    "document.querySelector('article')",
    "clone.querySelectorAll('script,style,noscript,nav,aside,footer,form,button,svg,iframe')",
    ".slice(0,60000)",
    "sourceUrl.equals(source.getUrl())",
    "showReaderModeDialog(title, body)",
):
    assert marker in reader, f"Reader Mode extraction guard missing: {marker}"
for marker in (
    "TextView article = text(body, 16, palette.text, Typeface.NORMAL)",
    "article.setTextIsSelectable(true)",
    'compactButton("Copy article text", true)',
    'setTitle(safeTitle)',
    'setPositiveButton("Done", null)',
    "ClipData.newPlainText(\"Daymark Reader Mode\", body)",
):
    assert marker in dialog, f"Reader Mode must render/copy plain extracted text: {marker}"
assert "loadUrl(" not in dialog and "loadDataWithBaseURL(" not in dialog, \
    "Reader Mode must not render extracted content as HTML or trigger another page load"
print("PASS: Reader Mode is user-triggered, HTTPS-only, bounded, local text-only, and separate from full-screen view")
