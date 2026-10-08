# Discussion prompt — Daymark browser extensions (for other models)

Copy/paste to ChatGPT / Claude / Gemini / etc.

---

## Context

We build **Daymark**, a privacy-first Android app (`com.cue.daymark`).  
The in-app browser is **Android System WebView**, not a Chromium embed (not Kiwi-sized).

Security policy forbids:

- `addJavascriptInterface` (no JS bridge to app data)
- `shouldInterceptRequest` (no request-level adblock)

We already ship:

- Local **Daymark packs** (JSON: matches + CSS + JS)
- Inject on `onPageFinished` via `evaluateJavascript`
- Built-ins (calm reading, hide noise CSS, link outline)
- Converters: Chrome **content_scripts** fragment → pack; ABP **cosmetic** `##` rules → CSS; minimal userscript header parse

## Goal

Make “add extension” as useful as possible **without** embedding full Chromium or breaking privacy/size.

## Questions for you

1. Best **lightweight** architecture on WebView for extension-like power (userscript manager, GM polyfill subset, cosmetic filters only, etc.)?
2. Which **conversion pipelines** are worth shipping in-app vs document-only (e.g. `to-userscript`, content_scripts only)?
3. Is a **limited** `GM_setValue` / `GM_getValue` via `WebView` localStorage or app `SharedPreferences` + carefully scoped bridge acceptable, or still too risky vs Daymark’s no-bridge rule?
4. UI: minimal “Extensions” manager (list, toggle, paste JSON/userscript, import cosmetic list) — must-have flows?
5. What should we **explicitly refuse** so users are not misled (“Chrome Web Store works”)?

## Constraints

- Stay WebView-based; no multi-MB browser engine.  
- Offline-first task app; browser Online is opt-in.  
- Prefer pure Java, no new heavy dependencies.  

## Deliverable

Prioritized options (Must / Should / Later), risks, and a concrete next 1–2 week implementation slice.

---
