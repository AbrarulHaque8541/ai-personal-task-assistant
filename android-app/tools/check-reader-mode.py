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
    "document.createTreeWalker(root,NodeFilter.SHOW_ELEMENT|NodeFilter.SHOW_TEXT", 
    "NodeFilter.FILTER_REJECT", 
    "node.getAttribute('aria-hidden')==='true'", 
    ".slice(0,maxChars)",
    "maxChars=60000,maxNodes=10000",
    "slice(0,120).trim()",
    "visited<maxNodes",
    "value=value.slice(0,remaining)",
    "length++;",

    "sourceUrl.equals(source.getUrl())",
    "showReaderModeDialog(title, body)",
):
    assert marker in reader, f"Reader Mode extraction guard missing: {marker}"
for marker in (
    'compactButton("A−", true)',
    'compactButton("A+", true)',
    'compactButton("Sans", true)',
    'compactButton("Light", true)',
    "readerFontSize[0] = Math.max(14f, readerFontSize[0] - 2f)",
    "readerFontSize[0] = Math.min(28f, readerFontSize[0] + 2f)",
    'String[] themeNames = {"Light", "Sepia", "Dark"}',
    "readerSerif[0] ? Typeface.SERIF : Typeface.SANS_SERIF",
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
assert "cloneNode(true)" not in reader, "Reader Mode should not clone the full page DOM and increase memory pressure"
print("PASS: Reader Mode is user-triggered, HTTPS-only, bounded, local text-only, and separate from full-screen view")
