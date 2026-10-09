// Options page: saves API key / language / custom filters into storage.sync.
// The background worker rebuilds the dynamic DNR rules on save.

const $ = (id) => document.getElementById(id);

const stored = await chrome.storage.sync.get("settings");
const settings = stored.settings || {};

$("api-url").value = settings.apiUrl || "";
$("api-origin").value = settings.apiOrigin || "";
$("api-key").value = settings.apiKey || "";
$("model").value = settings.model || "";
$("language").value = settings.language || "hi";
$("custom-filters").value = settings.customFilters || "";

$("save").onclick = async () => {
  const options = {
    apiUrl: $("api-url").value.trim(),
    apiOrigin: $("api-origin").value.trim().replace(/\/$/, ""),
    apiKey: $("api-key").value.trim(),
    model: $("model").value.trim(),
    language: $("language").value.trim() || "hi",
    customFilters: $("custom-filters").value,
  };
  await chrome.runtime.sendMessage({ type: "saveOptions", options });
  $("status").textContent = "Saved ✓";
  setTimeout(() => ($("status").textContent = ""), 2000);
};
