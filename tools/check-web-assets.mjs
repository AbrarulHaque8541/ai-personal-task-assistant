// Inspect the Cloudflare Workers static-asset package and fail if it contains anything
// that is not part of the intended web prototype (#43).
//
// This is the reproducible dry-run check the issue asked for: it never deploys and never
// touches production configuration, it only looks at the exact directory wrangler would
// upload.
//
// Usage: node tools/check-web-assets.mjs

import { readdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const assetDir = resolve(repoRoot, 'public-web');

const REQUIRED = ['index.html', 'app.js', 'task-logic.js', 'styles.css'];
const ALLOWED_EXTENSIONS = new Set(['.html', '.js', '.css', '.svg', '.png', '.ico', '.webmanifest', '.map']);
// Forbidden by path, substring or extension — case-insensitive.
const FORBIDDEN_EXTENSIONS = new Set(['.apk', '.aab', '.jar', '.jks', '.keystore', '.pem', '.env', '.log', '.zip', '.so', '.bin']);
const FORBIDDEN_SEGMENTS = ['.github', 'android-app', 'artifacts', 'tests', 'tools', 'node_modules', '.git'];
const FORBIDDEN_NAMES = ['package.json', 'package-lock.json', 'wrangler.jsonc', '.assetsignore',
  'readme.md', 'contributing.md', 'security.md', 'project_plan.md',
  'documentation_truth_audit.md', 'v1_acceptance.md', 'task_templates.md', 'license'];

async function walk(dir) {
  const out = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...(await walk(full)));
    else if (entry.isFile()) out.push(full);
  }
  return out;
}

function fail(message) {
  console.error(`FAIL: ${message}`);
  process.exitCode = 1;
}

async function main() {
  if (!existsSync(assetDir)) {
    fail(`${relative(repoRoot, assetDir)} does not exist — run "node tools/build-web-assets.mjs" first.`);
    return;
  }

  const files = await walk(assetDir);
  const rel = files.map((f) => relative(assetDir, f));
  const relLower = rel.map((r) => r.toLowerCase());

  for (const required of REQUIRED) {
    if (!relLower.includes(required)) fail(`required asset ${required} is missing`);
  }

  for (let i = 0; i < rel.length; i += 1) {
    const name = rel[i];
    const lower = relLower[i];
    const segments = lower.split(/[\\/]/);
    if (segments.some((s) => FORBIDDEN_SEGMENTS.includes(s))) {
      fail(`forbidden path in deployment package: ${name}`);
    }
    if (FORBIDDEN_NAMES.includes(segments[segments.length - 1])) {
      fail(`forbidden file in deployment package: ${name}`);
    }
    const dot = lower.lastIndexOf('.');
    const ext = dot >= 0 ? lower.slice(dot) : '';
    if (FORBIDDEN_EXTENSIONS.has(ext)) {
      fail(`forbidden file type in deployment package: ${name}`);
    } else if (!ALLOWED_EXTENSIONS.has(ext)) {
      fail(`unexpected file type in deployment package: ${name}`);
    }
  }

  if (process.exitCode) {
    console.error(`Deployment package inspection FAILED (${rel.length} file(s) inspected).`);
    return;
  }
  console.log(`PASS web deployment package: ${rel.length} web-only file(s), no non-web content.`);
  console.log(`   ${rel.join(', ')}`);
}

main().catch((error) => {
  console.error(`check-web-assets failed: ${error.message}`);
  process.exitCode = 1;
});
