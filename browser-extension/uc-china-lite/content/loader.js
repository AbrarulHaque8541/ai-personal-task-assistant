// UC China Lite — content loader.
// This is the ONLY content script declared in the manifest. It reads the
// user's enabled features and lazily imports just those feature modules —
// nothing runs on a page unless the user turned it on.

(async () => {
  let settings;
  try {
    const stored = await chrome.storage.sync.get("settings");
    settings = stored.settings || { features: {} };
  } catch (e) {
    return; // storage unavailable (rare); do nothing on this page
  }
  const enabled = settings.features || {};
  const base = chrome.runtime.getURL("features/");

  // ---- HOW TO ADD A NEW FEATURE -----------------------------------------
  // 1. Create features/yourfeature.js exporting the registry shape below.
  // 2. Add one line to features/registry.js.
  // That's it — the popup, options and this loader pick it up automatically.
  const contentFeatures = [
    "reader.js",
    "noimage.js",
    "darkmode.js",
    "aibutton.js",
    "translate.js",
    "gestures.js",
  ];
  for (const file of contentFeatures) {
    const id = file.replace(".js", "");
    try {
      if (enabled[id]) {
        const module = await import(base + file);
        if (module && typeof module.onEnable === "function") module.onEnable();
      }
    } catch (e) {
      // A failing feature must never break the rest of the page.
      console.debug("UC China Lite: feature failed to load:", id, e);
    }
  }
})();
