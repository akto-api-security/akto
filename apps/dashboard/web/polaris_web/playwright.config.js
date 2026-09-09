// @ts-check
const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests/e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  globalSetup: require.resolve('./tests/e2e/global-setup.js'),
  use: {
    baseURL: process.env.AKTO_BASE_URL || 'http://localhost:8080',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    storageState: './tests/e2e/.auth/user.json',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // Reuse the system-installed Chrome instead of downloading a separate Playwright
        // browser binary — no other Chromium/Playwright browser is installed in this environment.
        channel: 'chrome',
      },
    },
  ],
});
