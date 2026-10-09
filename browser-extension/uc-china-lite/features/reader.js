// Reader Mode: a minimal Readability-style extraction, written from
// scratch (no library). Scores candidate containers by text density and
// paragraph count, clones the winner into a clean full-screen overlay.

function pickArticle() {
  const candidates = document.querySelectorAll("article, main, div, section");
  let best = null;
  let bestScore = 0;
  for (const el of candidates) {
    if (!(el instanceof HTMLElement)) continue;
    const text = (el.innerText || "").trim();
    if (text.length < 250) continue; // too short to be the article
    const paragraphs = el.querySelectorAll("p").length;
    const links = el.querySelectorAll("a").length;
    // Density: lots of text, few links, several paragraphs.
    const score = text.length + paragraphs * 120 - links * 8;
    if (score > bestScore) { bestScore = score; best = el; }
  }
  return best;
}

function onEnable() {
  const article = pickArticle();
  if (!article) return; // nothing readable; feature silently does nothing
  const overlay = document.createElement("div");
  overlay.id = "uccl-reader";
  overlay.setAttribute("style", [
    "position:fixed", "inset:0", "z-index:2147483647", "overflow:auto",
    "background:#fbf8f1", "color:#1c1c1c", "padding:32px 16px",
    "font:18px/1.7 Georgia, 'Times New Roman', serif", "max-width:none",
  ].join(";"));
  const inner = document.createElement("div");
  inner.setAttribute("style", "max-width:680px;margin:0 auto");
  // Clean the clone: strip scripts, styles, forms and inline styles.
  const clone = article.cloneNode(true);
  clone.removeAttribute("style");
  clone.querySelectorAll("script, style, form, iframe, noscript").forEach((n) => n.remove());
  clone.querySelectorAll("*").forEach((n) => n.removeAttribute("style"));
  const title = document.createElement("h1");
  title.textContent = document.title;
  title.setAttribute("style", "font-size:1.5em;margin:0 0 16px;line-height:1.3");
  const close = document.createElement("button");
  close.textContent = "✕ Exit reader";
  close.setAttribute("style", "position:fixed;top:12px;right:12px;border:0;background:#333;color:#fff;padding:8px 12px;border-radius:6px;font:14px sans-serif;cursor:pointer");
  close.onclick = () => overlay.remove();
  inner.appendChild(title);
  inner.appendChild(clone);
  overlay.appendChild(inner);
  overlay.appendChild(close);
  document.documentElement.appendChild(overlay);
}

function onDisable() {
  const overlay = document.getElementById("uccl-reader");
  if (overlay) overlay.remove();
}

export { onEnable, onDisable };
export default { id: "reader", name: "Reader Mode", icon: "📖", description: "Clean reading view for articles.", defaultEnabled: false, onEnable, onDisable };
