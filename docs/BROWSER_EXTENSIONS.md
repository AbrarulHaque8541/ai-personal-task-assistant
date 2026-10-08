# Daymark browser extensions — research & real path

## Short answer

Daymark is a **WebView** browser (Android System WebView), not a full Chromium browser like Kiwi/Edge with Chrome Web Store support.

**There is a path** — not “every .crx works”, but a **converter middle layer**:

```
Chrome / Firefox extension  →  filter to content CSS/JS only  →  Daymark pack  →  inject on page
Userscript (.user.js)       →  parse @match + body            →  Daymark pack  →  inject on page
EasyList cosmetic ##rules   →  CSS element-hide               →  Daymark pack  →  inject on page
```

That is the same strategy used by lightweight WebView projects (WebMonkey userscripts, WebView injectors, cosmetic-only adblock scripts). Full store extensions need **Chrome’s extension process model**, which only exists inside a **Chromium embed** (large binary, Kiwi-class maintenance).

## What works vs what does not

| Source | After conversion |
|--------|------------------|
| Extension **content_scripts** (DOM/CSS) | ✅ often usable |
| Userstyles / Stylus CSS | ✅ |
| Greasemonkey/Tampermonkey scripts (DOM-only) | ⚠️ partial (no full GM_* suite) |
| EasyList **##cosmetic** hide rules | ✅ → CSS |
| EasyList **network** rules (`\|, $script`) | ❌ needs request interception |
| `chrome.tabs` / service worker / toolbar popup | ❌ |
| Full uBlock Origin | ❌ |

## Converter tools in this repo

| Class | Role |
|-------|------|
| `ExtensionChromeImport` | `manifest.json` + joined content JS/CSS → Daymark pack |
| `CosmeticFilterToCss` | ABP `##selector` lines → CSS pack |
| `ExtensionPackageParser` | Daymark JSON + minimal userscript header |
| `ExtensionRuntime` | Applies enabled packs on `onPageFinished` |

## Built-in packs

- **Calm reading** (default on)
- **Hide common noise** (off)
- **Link highlighter** (off)

## Security (unchanged)

- No `addJavascriptInterface` bridge to tasks/keys
- No `shouldInterceptRequest` (project policy)
- HTTPS pages only; Online still requires consent
- Third-party scripts are untrusted page code

## Why not “just change the engine a little”

Embedding Chromium for real `chrome.*` extensions is not a small patch — it is a multi‑MB browser fork (Kiwi’s whole product). Daymark stays WebView + local packs so install size and privacy model stay small.

## Practical install flow (for users / UI)

1. Prefer **userscript** or **CSS** sources when possible.  
2. Or unpack an extension → take `content_scripts` files + `matches` from `manifest.json` → convert via `ExtensionChromeImport` → save `.daymark-ext.json` in app private storage.  
3. Cosmetic filter lists: only `##` rules → `CosmeticFilterToCss`.  
4. Reload the page.
