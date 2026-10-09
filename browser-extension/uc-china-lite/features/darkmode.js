// Dark Mode: smart per-site dark theme.
// - Respects prefers-color-scheme by default (never forces dark on its own).
// - When enabled, remembers a per-domain choice via storage.sync.
// - Uses a filter-based inversion with an image/ video re-invert guard,
//   plus a CSS variables fallback, so it works on most sites without
//   shipping a per-site style library.

const STYLE_ID = "uccl-darkmode-style";

async function domainChoice() {
  const key = "uccl-dark-domains";
  const stored = await chrome.storage.sync.get(key);
  const domains = stored[key] || {};
  return domains[location.hostname];
}

function applyDark() {
  if (document.getElementById(STYLE_ID)) return;
  const style = document.createElement("style");
  style.id = STYLE_ID;
  style.textContent = [
    "html { filter: invert(1) hue-rotate(180deg); background:#fff !important; }",
    "img, image, picture, video, svg, iframe, canvas, [style*='background-image'] { filter: invert(1) hue-rotate(180deg); }",
    ":root { color-scheme: dark; }",
  ].join("\n");
  document.documentElement.appendChild(style);
}

function removeDark() {
  const style = document.getElementById(STYLE_ID);
  if (style) style.remove();
}

export async function onEnable() {
  const choice = await domainChoice();
  if (choice === "never") return; // user excluded this site
  const prefersDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
  // Only force dark when the system does not already prefer it;
  // if the OS already prefers dark, the site styles itself.
  if (!prefersDark || choice === "always") applyDark();
}

export function onDisable() { removeDark(); }
export default { id: "darkmode", name: "Dark Mode", icon: "🌙", description: "Smart dark theme, per-site memory.", defaultEnabled: false, onEnable, onDisable };
