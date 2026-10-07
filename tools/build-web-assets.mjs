// Build the Cloudflare Workers static-asset package for Daymark's web prototype.
//
// #43: the previous configuration set `assets.directory` to the repository root, so a
// `wrangler deploy` would have served whatever the ignore list happened to miss — APKs,
// the entire .github/ tree and repository docs. Here the published set is an explicit
// allowlist copied into ./public-web, so what ships is what we named, not what we forgot
// to exclude.
//
// Usage: node tools/build-web-assets.mjs

import { mkdir, cp, readdir, rm, stat } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const outDir = resolve(repoRoot, 'public-web');

// Everything the web prototype needs at runtime — and nothing else.
const STATIC_FILES = ['index.html', 'app.js', 'task-logic.js', 'styles.css'];
// Root-level assets that may be served (e.g. favicon.svg). Matched by extension.
const ALLOWED_ROOT_EXTENSIONS = ['.svg', '.png', '.ico', '.webmanifest'];

async function main() {
  if (existsSync(outDir)) {
    await rm(outDir, { recursive: true, force: true });
  }
  await mkdir(outDir, { recursive: true });

  const copied = [];
  for (const file of STATIC_FILES) {
    const source = resolve(repoRoot, file);
    if (!existsSync(source)) {
      throw new Error(`Required web file is missing: ${file}`);
    }
    await cp(source, resolve(outDir, file));
    copied.push(file);
  }

  const entries = await readdir(repoRoot, { withFileTypes: true });
  for (const entry of entries) {
    if (!entry.isFile()) continue;
    const dot = entry.name.lastIndexOf('.');
    if (dot <= 0) continue;
    const ext = entry.name.slice(dot).toLowerCase();
    if (!ALLOWED_ROOT_EXTENSIONS.includes(ext)) continue;
    await cp(resolve(repoRoot, entry.name), resolve(outDir, entry.name));
    copied.push(entry.name);
  }

  const size = (await stat(outDir)).size;
  console.log(`Built ${outDir} with ${copied.length} file(s): ${copied.join(', ')}`);
  console.log(`Package directory size: ${size} bytes`);
}

main().catch((error) => {
  console.error(`build-web-assets failed: ${error.message}`);
  process.exit(1);
});
