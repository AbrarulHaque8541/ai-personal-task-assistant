// Popup: reads the feature registry and auto-builds the toggle list.
// Adding a new feature module requires zero changes here.

import { REGISTRY, ORDER, getSettings } from "../features/registry.js";

const list = document.getElementById("features");

const settings = await getSettings();

for (const id of ORDER) {
  const feature = REGISTRY[id];
  const row = document.createElement("div");
  row.className = "feature";

  const meta = document.createElement("div");
  meta.className = "meta";
  const name = document.createElement("div");
  name.className = "name";
  name.textContent = `${feature.icon} ${feature.name}`;
  const desc = document.createElement("div");
  desc.className = "desc";
  desc.textContent = feature.description;
  meta.appendChild(name);
  meta.appendChild(desc);

  const toggle = document.createElement("input");
  toggle.type = "checkbox";
  toggle.id = "feature-" + id;
  toggle.checked = !!settings.features[id];
  toggle.setAttribute("aria-label", feature.name);
  toggle.onchange = async () => {
    settings.features[id] = toggle.checked;
    await chrome.storage.sync.set({ settings });
    // The background worker applies adblock/permission side effects.
    chrome.runtime.sendMessage({ type: "setFeature", id, enabled: toggle.checked });
  };

  row.appendChild(meta);
  row.appendChild(toggle);
  list.appendChild(row);
}

document.getElementById("open-options").onclick = () => {
  chrome.runtime.openOptionsPage();
};
