# Icons — placeholder instructions

The manifest intentionally ships without an `icons` entry so the extension
loads without binary assets. To add real icons:

1. Create PNGs named `icon16.png`, `icon48.png`, `icon128.png` in this folder
   (square, transparent background; a simple indigo circle with a white
   lightning bolt works well).
2. Add to `manifest.json`:

   "icons": { "16": "icons/icon16.png", "48": "icons/icon48.png", "128": "icons/icon128.png" }

3. Optional: `"action": { "default_icon": { ... } }` for a toolbar icon.
4. Reload the extension in chrome://extensions.

Any online PNG generator or `npx @svg2png/cli` can produce the three sizes.
