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

## Manager UI (shipped in v1.0.3)

- List + enable/disable toggle per pack (built-in vs user pack labeled).
- **Global kill switch** — "Run extensions in the browser"; when off, nothing is injected into any page (`ExtensionRuntime` checks `ExtensionStore.isGloballyEnabled()` first).
- Import (auto-detect Daymark JSON / userscript / ZIP-XPI-CRX) with review-before-install and a 5 MB cap.
- **Details** dialog per pack: source, id, state, run-at, CSS/JS sizes, full match/exclude lists, paused sites, explicit "Permissions: none" statement, and import warnings.
- **Sites** dialog per pack: pause/resume the pack on specific hosts (host or subdomain); sites are normalized (`example.com`, `https://example.com/page`, case/dot normalization; non-HTTPS/credentialed input rejected).
- **Export** user packs to a `.daymark-ext.json` document via SAF (`ACTION_CREATE_DOCUMENT`); built-ins are not exportable.
- **Remove** user packs with confirmation.
- `disabledSites` persist inside the pack file (user packs) or per-pack preferences (built-ins) and round-trip through `toDaymarkJson`/`parseDaymarkJson`.
- Match rules are scheme-strict: an `https://…` pattern never matches a cleartext URL (`BrowserExtension.MatchRules`).

## Signing / release note

Production APK publish must use the **same keystore** as existing v1.0.x installs. A signer mismatch must fail closed (already enforced in CI).
