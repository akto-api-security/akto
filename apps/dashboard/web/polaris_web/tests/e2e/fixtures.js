// Custom `test`/`expect` that every spec in this suite should import instead of '@playwright/test'
// directly. Ensures every test's page starts with the correct dashboardCategory in sessionStorage
// (see global-setup.js for why this can't just rely on clicking through the UI, or on storageState()
// round-tripping sessionStorage reliably) — belt-and-suspenders on top of global-setup's own write, so
// this holds regardless of exactly how Playwright's storageState() handles sessionStorage across runs.
const base = require('@playwright/test');

const REQUIRED_DASHBOARD_CATEGORY = 'Endpoint Security';

const test = base.test.extend({
  context: async ({ context }, use) => {
    await context.addInitScript((category) => {
      window.sessionStorage.setItem(
        'Akto-data',
        JSON.stringify({ state: { dashboardCategory: category }, version: 0 }),
      );
    }, REQUIRED_DASHBOARD_CATEGORY);
    await use(context);
  },
});

module.exports = { test, expect: base.expect };
