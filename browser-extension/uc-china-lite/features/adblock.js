// Ad blocker feature module.
// The blocking itself runs in the background service worker via
// declarativeNetRequest (rules/rules.json + user's custom list from
// options). This module only holds registry metadata for the popup UI.

export default {
  id: "adblock",
  name: "Ad Blocker",
  icon: "🛡",
  description: "Block common ads and trackers (declarativeNetRequest).",
  defaultEnabled: true,
  backgroundOnly: true, // no content script of its own
};
