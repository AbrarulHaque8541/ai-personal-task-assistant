# GeckoView Engine & WebExtensions Feasibility Study

## 1. Executive Summary & Objective

Daymark currently uses Android System WebView to provide a lightweight, zero-dependency, HTTPS-only browsing workspace. To address user requests for desktop-class browser extensions without compromising security or destabilizing the app, this study evaluates **Mozilla GeckoView** as an optional future engine adapter versus the current **System WebView + UserScript/Content-Script middle-layer**.

---

## 2. Technical Comparison: System WebView vs. GeckoView

| Metric / Dimension | Android System WebView (Current) | Mozilla GeckoView (Evaluated) | Practical Impact on Daymark |
| :--- | :--- | :--- | :--- |
| **APK Footprint** | ~190 KB – 220 KB (zero bundled engine) | ~45 MB – 55 MB per single ABI (~150 MB+ uncompressed multi-ABI) | 250× APK size increase. Sideload updates require substantial bandwidth. |
| **RAM Utilization** | ~40 MB – 80 MB (shares OS WebKit/Blink caches) | ~150 MB – 260 MB (independent Gecko runtime & IPC processes) | High memory pressure on low-tier devices (2GB–3GB RAM devices may suffer OOM). |
| **Cold Start Latency** | 150 ms – 350 ms (frequently pre-warmed by Android Zygote) | 650 ms – 1500 ms (must initialize Gecko runtime, libxul, SpiderMonkey) | Web workspace switch would exhibit noticeable latency unless pre-warmed. |
| **Extension Support** | Pure Content-Scripts / UserScripts / CSS via injection | Native WebExtensions (GeckoView `WebExtensionController`) | Real WebExtensions with background scripts, messaging, and storage. |
| **Chrome (.crx) Support** | Only extracted content_scripts & CSS via Daymark importer | **No native support.** Requires conversion to WebExtension / XPI. | Chrome-proprietary APIs (`chrome.gcm`, `chrome.syncFileSystem`) remain unsupported. |
| **Licensing** | Apache 2.0 / AOSP | Mozilla Public License 2.0 (MPL 2.0) | Permissive for inclusion, but requires source disclosure of GeckoView modifications. |
| **Min SDK** | 26 (Android 8.0) | 21+ (Officially supports API 26+) | Fully compatible with Daymark's `minSdk = 26`. |

---

## 3. WebExtension API Capabilities in GeckoView

When running GeckoView, the following APIs and capabilities are officially supported:

1. **Content Scripts:** Fully supported (matching URLs, run_at `document_start`, `document_end`, `document_idle`).
2. **Background Contexts:** Supported (MV2 background pages and MV3 event pages/workers).
3. **WebExtensions Storage:** Supported (`browser.storage.local`, `browser.storage.sync` with FxA).
4. **webRequest & webRequestBlocking:** Supported in GeckoView for Firefox-signed or debug extensions (used by uBlock Origin).
5. **declarativeNetRequest:** Supported in modern GeckoView MV3 builds.
6. **Tabs / Windows:** Emulated within embedding app constraints via `WebExtension.SessionController`.
7. **Downloads API:** Delegated to host application download listener.
8. **Native Messaging:** Supported through custom host application message handlers (`GeckoSession.setMessageDelegate`).

---

## 4. Crucial Restrictions & Realities

### A. The Signing Barrier (XPI Installation)
In release versions of Mozilla GeckoView, extension installation is restricted to signed add-ons from addons.mozilla.org (AMO) by default. Arbitrary un-signed local `.xpi` files fail to install unless:
- Using a custom Gecko runtime configuration with signature enforcement disabled (typically available only in GeckoView Beta/Nightly artifacts), OR
- Built-in extensions packaged directly within the APK assets (`installBuiltIn`).

### B. Chrome .CRX Compatibility Reality
GeckoView is **not** Chromium. It cannot install a binary `.crx` file directly. Any Chrome extension must be:
1. Extracted from the CRX3 container (ZIP payload).
2. Manifest converted from Chrome namespace to standard WebExtensions (`browser.*`).
3. Stripped of Google-proprietary APIs (`chrome.identity`, `chrome.enterprise`, etc.).

---

## 5. Architectural Blueprint: The `BrowserEngine` Abstraction

To preserve Daymark v1.0.3 stability while keeping a clean path for GeckoView in v1.1.0+, Daymark adopts a dual-engine adapter architecture:

```
                  ┌───────────────────────────────┐
                  │       MainActivity (UI)       │
                  └──────────────┬────────────────┘
                                 │
                  ┌──────────────▼────────────────┐
                  │       BrowserEngine (I)       │
                  ├───────────────────────────────┤
                  │ + loadUrl(url)                │
                  │ + goBack() / goForward()      │
                  │ + reload() / stop()           │
                  │ + setBlockNetwork(blocked)    │
                  │ + clearSiteData()             │
                  │ + getExtensionController()    │
                  └───────┬───────────────┬───────┘
                          │               │
        ┌─────────────────▼──┐         ┌──▼──────────────────┐
        │ DaymarkWebViewEngine│         │   GeckoViewEngine   │
        ├────────────────────┤         ├─────────────────────┤
        │ - Android WebView  │         │ - org.mozilla.gecko │
        │ - Zero APK overhead│         │ - Full WebExtension │
        │ - UserScript/CSS   │         │ - Heavy APK / RAM   │
        │ - Shipped in v1.0.3│         │ - Proposed (v1.1+)  │
        └────────────────────┘         └─────────────────────┘
```

---

## 6. Verdict & Implementation Recommendation for Daymark

1. **For Release v1.0.3:**
   - **Retain System WebView Engine:** It delivers an ultra-fast, zero-dependency 200 KB APK, zero memory bloat, and strict HTTPS security.
   - **Use Extracted Content-Script Converter:** Daymark's `ExtensionChromeImport` and `ExtensionPackageParser` safely extract userscripts, `.daymark-ext.json`, and manifest `content_scripts` without taking on 50MB of binary baggage.
2. **For Post-v1.0.3 / Advanced Variant:**
   - Offer a separate `gecko` product flavor (`app-githubSideloadGecko-release.apk`) for power users who demand full uBlock Origin and desktop-class extensions, keeping the core Daymark APK lightweight.
