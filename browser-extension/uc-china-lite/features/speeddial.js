// Speed Dial feature module: registry entry for the optional new-tab
// override (features/speeddial/newtab.html). The page itself checks the
// enabled flag; when disabled it explains how to turn it on, so the
// browser's default new tab is replaced only in appearance, never in data.

export default {
  id: "speeddial",
  name: "Speed Dial (New Tab)",
  icon: "⚡",
  description: "Clean quick-links new-tab page.",
  defaultEnabled: false, // off by default per lightweight policy
};
