// UC China Lite — feature registry.
// Every feature lives in its own module file and registers here.
// Adding a feature = create the module + add one entry here.

import adblock from "./adblock.js";
import reader from "./reader.js";
import noimage from "./noimage.js";
import darkmode from "./darkmode.js";
import aibutton from "./aibutton.js";
import translate from "./translate.js";
import speeddial from "./speeddial.js";
import gestures from "./gestures.js";

export const REGISTRY = {
  adblock,
  reader,
  noimage,
  darkmode,
  aibutton,
  translate,
  speeddial,
  gestures,
};

export const ORDER = [
  "adblock",
  "reader",
  "noimage",
  "darkmode",
  "aibutton",
  "translate",
  "speeddial",
  "gestures",
];

export async function getSettings() {
  const stored = await chrome.storage.sync.get("settings");
  const settings = stored.settings || {};
  settings.features = settings.features || {};
  for (const id of ORDER) {
    if (settings.features[id] === undefined) {
      settings.features[id] = REGISTRY[id].defaultEnabled;
    }
  }
  return settings;
}
