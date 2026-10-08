# Daymark browser extensions

## Honest scope

Daymark uses **Android System WebView**, not full Chromium/Firefox.

| Format | Support in Daymark |
|--------|---------------------|
| **Daymark pack** (`.daymark-ext.json`) | Full — recommended |
| **User CSS** (Stylus-like) | Yes — via pack `css` |
| **User scripts** (Tampermonkey-like, limited) | Partial — `js` + optional `@match` patterns; **no** `GM_*` APIs, no cross-origin XHR bridge |
| **Chrome Web Store / Firefox Add-ons (.crx / WebExtension)** | **Not supported** — those need `chrome.*` / `browser.*` APIs WebView does not provide |
| **uBlock Origin full engine** | **Not supported** — would need network interception; Daymark intentionally forbids `shouldInterceptRequest` for security policy |

Full Chrome/Firefox extension compatibility would require embedding a custom browser engine (multi‑MB, different product). Daymark stays lightweight and local-first.

## Daymark pack format (`.daymark-ext.json`)

```json
{
  "id": "com.example.focus",
  "name": "Focus reading",
  "version": "1.0.0",
  "description": "Larger text and calmer colors",
  "enabled": true,
  "matches": ["*://*/*"],
  "css": "body { max-width: 40rem; margin: 0 auto; line-height: 1.6; }",
  "js": "/* optional page script; no native bridge */",
  "runAt": "document_end"
}
```

- `matches`: simple patterns (`*://host/*`, `*://*/*`). Invalid URLs never match.
- `css` / `js`: injected on page load for matching HTTPS pages only.
- Scripts run **inside the page**; there is **no** `addJavascriptInterface` bridge to app data/tasks.
- Packs are stored under the app’s private files directory (local only).

## Built-in packs

1. **Calm reading** — readable typography CSS  
2. **Hide common noise** — CSS selectors for frequent clutter classes (best-effort, not an adblocker)  
3. **Link highlighter** — subtle outline on links  

Toggle built-ins and future user packs from **Web → overflow → Extensions** when the UI hook is present; packs still apply when enabled in storage even if UI is minimal.

## Security

- No native JS bridge to Daymark tasks or encryption keys.
- No request interception API for third-party filter lists.
- Online browsing still requires explicit Online consent.
- Only HTTPS main-frame pages receive injection.
- Treat third-party scripts like any untrusted code you paste into a page.
