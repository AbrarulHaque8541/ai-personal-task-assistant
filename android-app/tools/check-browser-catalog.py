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
assert "DEFAULT_ONLINE_ENABLED = false" in network, "fresh installs must start Offline"
assert "Browsing starts Offline." in providers, "provider guide must reflect the Offline default"
assert "requires the user's explicit **Enable Online** choice" in providers, "provider guide must explain the consent gate"
assert "enabling Online alone sends no request" in providers, "provider guide must not imply that consent itself requests a page"
assert "switch browsing Offline again from **Browser actions**" in providers
assert "remains offline until the user enables Online" not in providers, "stale offline-mode guidance"
assert "confirmBrowserOnlineAccess(() -> loadBrowserAddress(safeAddress))" in activity
assert "BROWSER_ONLINE_EXPLICIT_OPT_IN_KEY" in activity
print("PASS browser catalog and provider guide: catalog wiring, Offline default, explicit network consent, and user-action navigation gate agree")
