# Daymark Browser Engine Research — GeckoView / WebExtensions

Status: research note. Nothing in the current WebView implementation changes as a
result of this document. Date: 2026-10-08.

## Conclusion (short)

* WebView cannot run Chrome Web Store extensions. Daymark's cosmetic/content-script
  pack model (current main) is the correct maximum for WebView.
* GeckoView is the only realistic path to real WebExtensions on Android without
  shipping a full Chromium fork. Mozilla designs GeckoView for this and exposes
  `WebExtensionController` (install / enable / disable / uninstall / list) plus
  extension messaging.
* Recommended architecture (future, not v1.0.3):

```
BrowserEngine (interface)
 ├── DaymarkWebViewEngine   (stable, current behavior)
 └── GeckoViewEngine        (optional, feature-gated "advanced web mode")
```

## What GeckoView can actually support

| Capability | Supported? | Notes |
|---|---|---|
| Firefox WebExtensions | Yes | Real engine support, not emulation |
| Built-in bundled XPI | Yes | `WebExtensionController.ensureBuiltIn("resource://android/assets/....xpi", id)` |
| Mozilla-signed XPI (e.g. uBlock Origin) | Yes | `install()` with a PromptDelegate; XPI must be Mozilla-signed |
| Arbitrary unsigned user XPI | No (release builds) | Signing requirement blocks sideloaded/unsigned XPIs |
| Chrome .crx | No | Chrome-only packaging; not accepted by Gecko |
| Manifest V2 | Yes | Full |
| Manifest V3 | Partially | Firefox MV3 uses event pages, not service workers; `webRequest` blocking still allowed (unlike Chrome MV3) |
| content scripts | Yes | |
| permissions prompts | Yes | Via `PromptDelegate` |
| downloads API | Yes (extension-side) | |
| tabs API | Yes | |
| storage (extension) | Yes | Browser storage, not app task storage |
| webRequest / blocking | Yes | Real request-level blocking becomes possible — this is the big win |
| declarativeNetRequest | Partially | Supported by Firefox; semantics differ from Chrome |
| native messaging | Yes | `setMessageDelegate`; requires `nativeMessaging`, `geckoViewAddons` permissions (built-in/privileged only for the latter) |

## Cost / risk measurements to run before any adoption

1. APK size delta (GeckoView AAB adds roughly 40–70 MB depending on ABI splits).
2. Startup time and memory (GeckoView spawns a separate Gecko child process).
3. Battery (extra process lifetime; the Online web mode is opt-in, which helps).
4. Android API floor: GeckoView current builds require API 21+; Daymark min is 26 — OK.
5. Licensing: MPL 2.0 — compatible with a proprietary app shell, attribution needed.
6. Security implications: extension code gets real privileged APIs; the current
   "extension code is untrusted, page-local only" posture must be re-reviewed.
   Daymark task data must never be exposed to extension processes.

## Truthful compatibility statement for users

* Chrome Web Store CRX: will never work in WebView; not supported by Gecko either.
* Firefox AMO XPI (signed, Android-compatible): possible only under GeckoView mode.
* Conversion path stays as-is: content_scripts / userscripts / cosmetic CSS →
  Daymark pack, with explicit warnings on everything dropped.

## Decision

Do not destabilize v1.0.3. Ship WebView. Keep `BrowserEngine` abstraction as a
follow-up issue; prototype GeckoView mode in a side branch measuring the six
points above before any user-facing switch exists.

## Sources

* Firefox source docs — GeckoView WebExtensions (installBuiltIn, messaging):
  https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html
* GeckoView WebExtensionController API docs:
  https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtension.html
* Real-world embed (uBlock Origin XPI via ensureBuiltIn):
  https://github.com/mkaafi6/muufi-gecko
