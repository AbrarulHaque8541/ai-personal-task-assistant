const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..');
const css = fs.readFileSync(path.join(root, 'styles.css'), 'utf8');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');

// The dark theme block only — stop before the reduced-motion block, which legitimately
// uses !important and is not part of the colour scheme.
const darkStart = css.indexOf('@media (prefers-color-scheme: dark)');
const darkEnd = css.indexOf('@media (prefers-reduced-motion', darkStart);
const dark = css.slice(darkStart, darkEnd === -1 ? undefined : darkEnd);

test('the web prototype ships a dark theme via prefers-color-scheme', () => {
  assert.ok(darkStart !== -1, 'a dark-mode media query is required');
  assert.match(dark, /color-scheme:\s*dark/, 'the dark theme must declare color-scheme: dark');
});

test('the dark theme overrides the core surface tokens', () => {
  for (const token of ['--paper', '--surface', '--ink', '--muted', '--line', '--green', '--shadow']) {
    assert.ok(dark.includes(token + ':'), `dark theme must redefine ${token}`);
  }
});

test('the dark theme covers every hard-coded light surface', () => {
  // These selectors used literal light colours in the light theme, so each must be
  // re-stated in the dark block or it would stay bright on a dark background.
  for (const selector of [
    '.rail', '.local-badge', '.card', '.add-form input', '.button-quiet',
    '.task-row', '.complete-button', '.task-title', '.priority-high',
    '.empty-state', '.suggestion-card', '.suggestion-item', '.edit-dialog',
    '.toast', '.page-footer'
  ]) {
    assert.ok(dark.includes(selector), `dark theme must restyle ${selector}`);
  }
});

test('the browser chrome colour follows the colour scheme', () => {
  assert.match(html, /<meta name="theme-color" content="#f6f7f4" media="\(prefers-color-scheme: light\)">/);
  assert.match(html, /<meta name="theme-color" content="#12160f" media="\(prefers-color-scheme: dark\)">/);
});

test('the dark theme does not rely on !important overrides', () => {
  assert.ok(!dark.includes('!important'), 'dark theme must not use !important');
});
