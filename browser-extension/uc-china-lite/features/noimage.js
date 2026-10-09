// No-Image mode: hides all images on the page with a single stylesheet.
// Styles are injected lazily and removed cleanly when toggled off.

const STYLE_ID = "uccl-noimage-style";

function apply() {
  let style = document.getElementById(STYLE_ID);
  if (!style) {
    style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent =
      "img, image, picture, video, [style*='background-image'] { visibility: hidden !important; }";
    document.documentElement.appendChild(style);
  }
}

function remove() {
  const style = document.getElementById(STYLE_ID);
  if (style) style.remove();
}

export function onEnable() { apply(); }
export function onDisable() { remove(); }
export default { id: "noimage", name: "No-Image Mode", icon: "🖼", description: "Hide all images on pages.", defaultEnabled: false, onEnable, onDisable };
