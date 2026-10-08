# Daymark browser extensions

## Positioning (multi-model consensus)

Daymark packs **customize the current WebView page** (CSS + page JS).  
They are **not** Chrome extensions and do **not** provide browser privileges, network blocking, or private Daymark data access.

Suggested in-app copy:

> Daymark packs only customize the current WebView page. They are not Chrome extensions and do not provide browser privileges, network blocking, or private Daymark data access.

## Must (shipped / in progress)

| Item | Status |
|------|--------|
| Daymark JSON packs (matches, excludes, css, js, runAt, warnings) | ✅ |
| `onPageFinished` injection + SPA bootstrap (history + MutationObserver) | ✅ |
| Userscript parse: `@name` `@match` `@exclude` `@run-at` `@grant none` | ✅ |
| `GM_addStyle` only (in-page); **no** `GM_getValue`/`setValue` in v1 | ✅ |
| ABP cosmetic `##` → CSS | ✅ |
| Chrome content_scripts CSS/JS fragment import | ✅ |
| Pack size / enabled-per-page limits | ✅ |
| Import warnings for unsupported grants / `@require` | ✅ |

## Explicitly refuse

- Chrome Web Store / `.crx` / full MV3  
- Network filters (`\|\|ads^`) / uBlock scriptlets  
- `addJavascriptInterface` bridge  
- `shouldInterceptRequest`  
- Remote `@require` / `@resource` fetch  
- Guaranteed `document-start` (System WebView; optional androidx.webkit later, currently no new deps)  

## GM storage decision (consensus)

- **No** SharedPreferences + bridge.  
- **v1:** no `GM_getValue` / `GM_setValue`.  
- **Later (optional):** pure `localStorage` shim, page-visible, not private — only after security review.

## Manager UI (Should — next)

List + toggle, paste import (auto-detect), review-before-enable, export, per-site disable, global kill switch.

## Signing / release note

Production APK publish must use the **same keystore** as existing v1.0.x installs. A signer mismatch must fail closed (already enforced in CI).


## v1.0.3 compatibility importer

Daymark now has a compatibility import path for ZIP/XPI-style WebExtensions and CRX3 packages.

The importer reads manifest.json and converts only the extension's page-local content_scripts into a Daymark pack:

- matches / exclude_matches -> Daymark match rules
- css files -> injected page CSS
- js files -> injected page JS
- run_at -> best-effort Daymark injection timing
- bundled files are read from the selected local archive; Daymark does not fetch extension resources from the network
- Manifest V2/V3 background pages, service workers, toolbar actions/popups, native messaging, chrome.* / browser.* privileged APIs, and network interception are not executed
- requested permissions are not granted; the imported pack remains page-local and subject to Daymark's HTTPS-only runtime

This is intentionally a conversion/compatibility layer, not a claim that arbitrary Chrome/Firefox extensions become fully compatible.

Chrome content scripts are designed around matches, exclude_matches, CSS/JS files and run_at, which are the parts Daymark can safely translate into its page-local model. Privileged extension APIs remain outside this compatibility boundary. 

## Engine research

A full WebExtension runtime is technically possible on Android without writing a browser engine from scratch: Mozilla's GeckoView is an embeddable Gecko engine and exposes a WebExtensionController for installing/running WebExtensions. Mozilla documents native messaging and extension lifecycle management as well. 

For Daymark, the recommended architecture is therefore:

1. v1.0.3: keep WebView as the default/lightweight engine and ship the compatibility importer.
2. Next engine experiment: prototype a separate GeckoView-backed browser surface behind an explicit Advanced Extension Engine setting.
3. Only after real-device profiling: decide whether GeckoView should become the default or remain an optional engine. Measure APK size, startup time, RAM, thermal/battery cost, page compatibility and extension coverage.
4. Do not promise Chrome Web Store plug-and-play until an actual engine/API compatibility test suite passes.

The native Android WebView remains a Chromium-based embedded view and is not itself a drop-in Chrome extension host.
