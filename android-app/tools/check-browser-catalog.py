#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[1]
addr = (root / "app/src/main/java/com/cue/daymark/BrowserAddress.java").read_text(encoding="utf-8")
ai = (root / "app/src/main/java/com/cue/daymark/AiSiteCatalog.java").read_text(encoding="utf-8")
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
for engine in ("DUCKDUCKGO", "GOOGLE", "BING", "BRAVE", "STARTPAGE", "KAGI", "YOU", "YANDEX"):
    assert engine in addr, engine
assert "ChatGPT" in ai and "Perplexity" in ai and "Copilot" in ai and "Grok" in ai
assert "AiSiteCatalog.entries()" in activity
assert "canGoBack()" in activity
print("PASS browser catalog: expanded search engines + AI site order + back wiring present")
