#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[1]
addr = (root / "app/src/main/java/com/cue/daymark/BrowserAddress.java").read_text(encoding="utf-8")
ai = (root / "app/src/main/java/com/cue/daymark/AiSiteCatalog.java").read_text(encoding="utf-8")
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
for engine in ("DUCKDUCKGO", "GOOGLE", "BING", "BRAVE", "STARTPAGE", "KAGI", "YOU", "YANDEX"):
    assert engine in addr, engine
assert "ChatGPT" in ai and "Perplexity" in ai and "Copilot" in ai and "Grok" in ai
assert "for (BrowserAddress.SearchEngine engine : BrowserAddress.SearchEngine.values())" in activity
assert "isBottomShortcutProvider(engine)" in activity and "isAiProvider(engine)" in activity
assert "browserSearchShortcutButton(engine)" in activity and "browserLastSearchQuery" in activity
assert "canGoBack()" in activity
print("PASS browser catalog: common web engines + AI shortcuts + current-query switching + back wiring present")
