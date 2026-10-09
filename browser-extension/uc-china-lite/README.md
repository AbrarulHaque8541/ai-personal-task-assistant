# UC China Lite — Chromium extension (Manifest V3)

Lightweight, modular client-side features for Chrome / Edge / Brave:
adblock (declarativeNetRequest), reader mode, no-image mode, smart dark mode,
AI assistant button (own API key, OpenAI-compatible), quick translate,
optional speed-dial new tab and mouse gestures.

- Pure vanilla JS, no frameworks, no CDNs, no telemetry. Total size ~60 KB.
- One content loader lazily imports only the feature modules you enable.
- Settings live in `chrome.storage.sync`.

## Install (Chrome / Edge / Brave)

1. Open `chrome://extensions` (Edge: `edge://extensions`, Brave: `brave://extensions`).
2. Turn **Developer mode** ON (top-right toggle).
3. Click **Load unpacked**.
4. Select this `uc-china-lite` folder (the one containing `manifest.json`).

## Adding a new feature later

1. Create `features/yourfeature.js`:

   export default { id: "yourfeature", name: "Your Feature", icon: "⭐",
                    description: "What it does.", defaultEnabled: false,
                    onEnable() { /* DOM work here */ }, onDisable() { } };

2. Register it in `features/registry.js` (one import + one REGISTRY entry + ORDER slot).
3. If it should run on pages, add its filename to `contentFeatures` in
   `content/loader.js`.

That's the whole pattern — popup UI, options and storage all follow the
registry automatically.
