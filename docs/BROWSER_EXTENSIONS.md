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
