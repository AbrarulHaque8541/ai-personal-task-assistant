// UC China Lite — background service worker (module).
// Responsibilities: toggle the static DNR ruleset, rebuild dynamic rules
// from the user's custom filter list, and proxy AI requests so the content
// script never needs broad host permissions of its own.

import { REGISTRY } from "./features/registry.js";

const SETTINGS_KEY = "settings";

/** Read settings, applying each feature's defaultEnabled when unset. */
async function getSettings() {
  const stored = await chrome.storage.sync.get(SETTINGS_KEY);
  const settings = stored[SETTINGS_KEY] || {};
  settings.features = settings.features || {};
  for (const feature of Object.values(REGISTRY)) {
    if (settings.features[feature.id] === undefined) {
      settings.features[feature.id] = feature.defaultEnabled;
    }
  }
  return settings;
}

/** Turn the shipped adblock ruleset on/off. */
async function setAdblockEnabled(enabled) {
  await chrome.declarativeNetRequest.updateEnabledRulesets({
    enableRulesetIds: enabled ? ["rules_1"] : [],
    disableRulesetIds: enabled ? [] : ["rules_1"],
  });
}

/** Convert one custom filter line into a DNR rule.
 *  Supported forms (EasyList-like subset):
 *    ||domain.com^   -> block any request whose host ends with domain.com
 *    /path/segment/  -> block URLs containing the path segment
 *    anything else   -> treated as a plain substring of the URL
 */
function ruleFromLine(line, index) {
  const raw = line.trim();
  if (!raw || raw.startsWith("!") || raw.startsWith("[")) return null;
  const condition = { isUrlFilterCaseSensitive: false, resourceTypes: undefined };
  let urlFilter = raw;
  if (urlFilter.startsWith("||")) {
    urlFilter = urlFilter.slice(2);
    if (urlFilter.endsWith("^")) urlFilter = urlFilter.slice(0, -1);
    condition.requestDomains = [urlFilter];
    urlFilter = null;
  } else {
    if (urlFilter.startsWith("|")) urlFilter = urlFilter.slice(1);
    if (urlFilter.endsWith("^") || urlFilter.endsWith("|")) urlFilter = urlFilter.slice(0, -1);
    if (!urlFilter) return null;
    condition.urlFilter = urlFilter;
  }
  return {
    id: 1000 + index,
    priority: 1,
    action: { type: "block" },
    condition,
  };
}

/** Replace all dynamic rules built from the user's custom filter list. */
async function rebuildCustomRules(filterText) {
  const existing = await chrome.declarativeNetRequest.getDynamicRules();
  const removeRuleIds = existing.map((r) => r.id);
  const rules = (filterText || "")
    .split("\n")
    .map(ruleFromLine)
    .filter(Boolean)
    .slice(0, 5000); // stay far below MV3 dynamic rule limits
  await chrome.declarativeNetRequest.updateDynamicRules({
    removeRuleIds,
    addRules: rules,
  });
}

chrome.runtime.onInstalled.addListener(async () => {
  const settings = await getSettings();
  await setAdblockEnabled(settings.features.adblock !== false);
  await rebuildCustomRules(settings.customFilters || "");
});

chrome.runtime.onStartup.addListener(async () => {
  const settings = await getSettings();
  await setAdblockEnabled(settings.features.adblock !== false);
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  (async () => {
    const settings = await getSettings();
    switch (message && message.type) {
      case "getSettings":
        sendResponse(settings);
        break;
      case "setFeature": {
        const { id, enabled } = message;
        settings.features[id] = enabled;
        await chrome.storage.sync.set({ [SETTINGS_KEY]: settings });
        if (id === "adblock") await setAdblockEnabled(enabled);
        if (id === "adblock" && enabled) await rebuildCustomRules(settings.customFilters || "");
        sendResponse({ ok: true });
        break;
      }
      case "saveOptions": {
        Object.assign(settings, message.options);
        await chrome.storage.sync.set({ [SETTINGS_KEY]: settings });
        await rebuildCustomRules(settings.customFilters || "");
        if (message.options.apiOrigin) {
          // Requested here so the user only grants access to their chosen API host.
          try {
            await chrome.permissions.request({ origins: [message.options.apiOrigin + "/*"] });
          } catch (e) {
            // Without a user gesture this may fail; AI calls then fall back to a search tab.
          }
        }
        sendResponse({ ok: true });
        break;
      }
      case "aiRequest": {
        const { prompt } = message;
        const { apiKey, apiUrl, model } = settings;
        if (!apiKey || !apiUrl) {
          sendResponse({ error: "no-api-key" });
          break;
        }
        try {
          const response = await fetch(apiUrl, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              Authorization: "Bearer " + apiKey,
            },
            body: JSON.stringify({
              model: model || "gpt-4o-mini",
              messages: [{ role: "user", content: prompt }],
            }),
          });
          const data = await response.json();
          const text =
            data &&
            data.choices &&
            data.choices[0] &&
            data.choices[0].message &&
            data.choices[0].message.content;
          sendResponse(text ? { text: String(text) } : { error: "bad-response" });
        } catch (e) {
          sendResponse({ error: String(e) });
        }
        break;
      }
      default:
        sendResponse({ error: "unknown-message" });
    }
  })();
  return true; // keep the message channel open for the async sendResponse
});
