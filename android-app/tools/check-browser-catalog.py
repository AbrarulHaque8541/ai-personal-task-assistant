#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[1]
addr = (root / "app/src/main/java/com/cue/daymark/BrowserAddress.java").read_text(encoding="utf-8")
ai = (root / "app/src/main/java/com/cue/daymark/AiSiteCatalog.java").read_text(encoding="utf-8")
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
network = (root / "app/src/main/java/com/cue/daymark/BrowserNetworkPolicy.java").read_text(encoding="utf-8")
providers = (root / "BROWSER_SEARCH_PROVIDERS.md").read_text(encoding="utf-8")
for engine in ("DUCKDUCKGO", "GOOGLE", "BING", "BRAVE", "STARTPAGE", "KAGI", "YOU", "YANDEX"):
    assert engine in addr, engine
assert "ChatGPT" in ai and "Perplexity" in ai and "Copilot" in ai and "Grok" in ai
assert "for (BrowserAddress.SearchEngine engine : BrowserAddress.SearchEngine.values())" in activity
assert "isBottomShortcutProvider(engine)" in activity and "isAiProvider(engine)" in activity
assert "browserSearchShortcutButton(engine)" in activity and "browserLastSearchQuery" in activity
assert "canGoBack()" in activity
assert "DEFAULT_ONLINE_ENABLED = true" in network, "browser network default must match provider documentation"
assert "Browsing is enabled by default." in providers, "provider guide must reflect the current online default"
assert "only after you tap **Go** or a provider/site shortcut" in providers, "provider guide must state the user-action gate"
assert "Turn off online browsing" in activity and "turn online browsing off from **Browser actions**" in providers
assert "remains offline until the user enables Online" not in providers, "stale offline-by-default claim"
print("PASS browser catalog and provider guide: catalog wiring, user-action network gate, and default-state documentation agree")
