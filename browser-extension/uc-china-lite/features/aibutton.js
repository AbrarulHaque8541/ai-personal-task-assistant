// AI Assistant button: a small floating button appears when the user
// selects text. Clicking it opens a panel with Explain / Summarize /
// Translate. With an OpenAI-compatible key+URL configured in options the
// request goes through the background worker; without a key every action
// falls back to opening a search tab with the selected text.

const BTN_ID = "uccl-ai-btn";
const PANEL_ID = "uccl-ai-panel";

function selectedText() {
  const sel = window.getSelection && window.getSelection();
  return sel ? String(sel).trim() : "";
}

function buildButton() {
  const btn = document.createElement("div");
  btn.id = BTN_ID;
  btn.textContent = "✨";
  btn.title = "UC China Lite AI";
  btn.setAttribute("style", [
    "position:absolute", "z-index:2147483646", "width:34px", "height:34px",
    "line-height:34px", "text-align:center", "border-radius:50%",
    "background:#4f46e5", "color:#fff", "font-size:16px", "cursor:pointer",
    "box-shadow:0 2px 6px rgba(0,0,0,.3)", "user-select:none", "display:none",
  ].join(";"));
  return btn;
}

function buildPanel(text) {
  const panel = document.createElement("div");
  panel.id = PANEL_ID;
  panel.setAttribute("style", [
    "position:fixed", "bottom:24px", "right:24px", "z-index:2147483646",
    "width:320px", "max-height:50vh", "overflow:auto", "padding:12px",
    "background:#fff", "color:#111", "border:1px solid #ddd", "border-radius:10px",
    "box-shadow:0 6px 24px rgba(0,0,0,.2)", "font:14px/1.5 sans-serif",
  ].join(";"));
  const row = document.createElement("div");
  row.setAttribute("style", "display:flex;gap:6px;margin-bottom:8px");
  for (const action of ["Explain", "Summarize", "Translate"]) {
    const b = document.createElement("button");
    b.textContent = action;
    b.setAttribute("style", "flex:1;border:1px solid #ccc;background:#f5f5f5;border-radius:6px;padding:6px;font:13px sans-serif;cursor:pointer");
    b.onclick = () => runAction(action, text, panel);
    row.appendChild(b);
  }
  const snippet = document.createElement("div");
  snippet.textContent = text.length > 120 ? text.slice(0, 120) + "…" : text;
  snippet.setAttribute("style", "color:#666;font-size:12px");
  const close = document.createElement("button");
  close.textContent = "✕";
  close.setAttribute("style", "position:absolute;top:4px;right:6px;border:0;background:none;cursor:pointer;color:#999");
  close.onclick = () => panel.remove();
  panel.appendChild(close);
  panel.appendChild(snippet);
  panel.appendChild(row);
  const out = document.createElement("div");
  out.id = PANEL_ID + "-out";
  panel.appendChild(out);
  return panel;
}

async function runAction(action, text, panel) {
  const out = document.getElementById(PANEL_ID + "-out");
  if (out) out.textContent = "…";
  const prompt = action === "Explain"
    ? "Explain this clearly:\n\n" + text
    : action === "Summarize"
      ? "Summarize this in a few bullet points:\n\n" + text
      : "Translate this to the user's preferred language:\n\n" + text;
  const response = await chrome.runtime.sendMessage({ type: "aiRequest", prompt });
  if (out) {
    if (response && response.text) out.textContent = response.text;
    else out.textContent = "No API key set — opened a search tab instead.";
  }
  if (!response || !response.text) {
    window.open("https://www.google.com/search?q=" + encodeURIComponent(prompt), "_blank");
  }
}

function onEnable() {
  if (document.getElementById(BTN_ID)) return;
  const btn = buildButton();
  document.addEventListener("selectionchange", () => {
    const text = selectedText();
    if (!text) { btn.style.display = "none"; return; }
    const rect = (window.getSelection().rangeCount > 0)
      ? window.getSelection().getRangeAt(0).getBoundingClientRect()
      : null;
    if (rect && rect.width + rect.height > 0) {
      btn.style.left = Math.max(4, rect.left) + "px";
      btn.style.top = Math.max(4, rect.top - 40) + "px";
      btn.style.display = "block";
    }
  });
  btn.onclick = () => {
    const text = selectedText();
    if (!text) return;
    document.getElementById(PANEL_ID)?.remove();
    document.documentElement.appendChild(buildPanel(text));
  };
  document.documentElement.appendChild(btn);
}

function onDisable() {
  document.getElementById(BTN_ID)?.remove();
  document.getElementById(PANEL_ID)?.remove();
}

export { onEnable, onDisable };
export default { id: "aibutton", name: "AI Assistant", icon: "✨", description: "Floating AI button for selected text.", defaultEnabled: false, onEnable, onDisable };
