const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const logic = require('../task-logic.js');

const APP_SOURCE = fs.readFileSync(path.join(__dirname, '..', 'app.js'), 'utf8');
const STORAGE_KEY = 'daymark.tasks.v1';
const BACKUP_KEY = `${STORAGE_KEY}.backup`;

function bootApp({ savedValue, failBackup = false }) {
  const elements = new Map();
  const storedValues = new Map([[STORAGE_KEY, savedValue]]);

  function element(selector) {
    if (!elements.has(selector)) {
      const attributes = new Map();
      const classes = new Set();
      elements.set(selector, {
        textContent: '',
        value: '',
        hidden: false,
        innerHTML: '',
        open: false,
        dataset: {},
        classList: {
          add: (name) => classes.add(name),
          remove: (name) => classes.delete(name),
          toggle: (name, force) => {
            const enabled = force === undefined ? !classes.has(name) : force;
            if (enabled) classes.add(name);
            else classes.delete(name);
            return enabled;
          }
        },
        addEventListener() {},
        setAttribute: (name, value) => attributes.set(name, value),
        removeAttribute: (name) => attributes.delete(name),
        querySelector: (childSelector) => element(`${selector} ${childSelector}`),
        focus() {},
        showModal() { this.open = true; },
        close() { this.open = false; }
      });
    }
    return elements.get(selector);
  }

  const localStorage = {
    getItem(key) {
      return storedValues.has(key) ? storedValues.get(key) : null;
    },
    setItem(key, value) {
      if (key === BACKUP_KEY && failBackup) throw new Error('Quota exceeded');
      storedValues.set(key, String(value));
    }
  };
  const document = {
    hidden: false,
    querySelector: element,
    querySelectorAll: () => [],
    addEventListener() {}
  };
  const window = {
    DaymarkLogic: logic,
    addEventListener() {}
  };

  vm.runInNewContext(APP_SOURCE, {
    Date,
    Intl,
    Math,
    console: { warn() {} },
    document,
    localStorage,
    window,
    clearTimeout() {},
    setTimeout: () => 1
  }, { filename: 'app.js' });

  return { elements, storedValues };
}

test('corrupt saved data is copied to a browser backup and reported as preserved', () => {
  const corruptData = '{not valid JSON';
  const { elements, storedValues } = bootApp({ savedValue: corruptData });

  assert.equal(storedValues.get(BACKUP_KEY), corruptData);
  assert.match(elements.get('#toast').textContent, /kept in a browser backup/);
});

test('backup failure is reported honestly and leaves the original entry untouched', () => {
  const corruptData = '{not valid JSON';
  const { elements, storedValues } = bootApp({ savedValue: corruptData, failBackup: true });

  assert.equal(storedValues.has(BACKUP_KEY), false);
  assert.equal(storedValues.get(STORAGE_KEY), corruptData);
  assert.match(elements.get('#toast').textContent, /could not create a recovery backup/);
});
