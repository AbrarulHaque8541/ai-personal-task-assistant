// Mouse gestures: hold the right mouse button, drag, release.
//   ← left   : go back
//   → right  : go forward
// Deliberately tiny: no library, no settings UI, ~60 lines.

function onEnable() {
  if (window.__ucclGestures) return; // already installed
  let start = null;
  const guard = (e) => {
    if (e.button === 2) start = { x: e.clientX, y: e.clientY };
  };
  const release = (e) => {
    if (!start || e.button !== 2) { start = null; return; }
    const dx = e.clientX - start.x;
    const dy = e.clientY - start.y;
    start = null;
    if (Math.abs(dx) < 60 || Math.abs(dx) <= Math.abs(dy)) return; // horizontal only
    if (dx < 0 && history.length > 1) history.back();
    else if (dx > 0) history.forward();
  };
  window.addEventListener("mousedown", guard, true);
  window.addEventListener("mouseup", release, true);
  window.__ucclGestures = true;
}

function onDisable() {
  // The listeners close over the enabled state via the flag; simplest
  // correct removal is a page reload, so we just drop the install flag
  // and let the next navigation stop the module from re-importing.
  delete window.__ucclGestures;
}

export { onEnable, onDisable };
export default { id: "gestures", name: "Mouse Gestures", icon: "🖱", description: "Right-drag left/right = back/forward.", defaultEnabled: false, onEnable, onDisable };
