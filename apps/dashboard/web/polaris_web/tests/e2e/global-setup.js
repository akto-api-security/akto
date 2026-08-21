// One-time auth bootstrap for the local Playwright suite. Logging in through the real form isn't
// possible for this seeded dev user (see README.md in this dir) — instead we reuse the most recent
// refreshToken already issued to them, stored in the local Mongo's common.users collection, exactly
// as the real login flow would have left it. Then we switch to the target test account (this user
// has 3: "My account" is the default `user.findAnyAccountId()` picks, which is NOT the one with
// ATLAS/agentic data — every API call 403s under it, since it doesn't have the necessary role there).
// Saves a storageState file every project reuses, so individual test files never need to deal with
// auth or account selection.
const { chromium } = require('@playwright/test');
const { execSync } = require('child_process');
const path = require('path');

const AUTH_FILE = path.join(__dirname, '.auth', 'user.json');
const LOGIN_EMAIL = process.env.AKTO_TEST_EMAIL || 'rakshak@akto.io';
const BASE_URL = process.env.AKTO_BASE_URL || 'http://localhost:8080';
const TEST_ACCOUNT_NAME = process.env.AKTO_TEST_ACCOUNT_NAME || 'Atlas Scale Test';
// Fixed so the coordinate-based dropdown clicks below stay correct — Playwright's getByRole/getByText
// couldn't resolve this account switcher (it's a Polaris combobox rendered through a portal/overlay
// whose accessible name Playwright doesn't compute the way its own ARIA snapshot dump suggests it
// should — tried both button- and combobox-role locators, both timed out). Coordinates are the only
// approach that reliably worked; contained to this one bootstrap step, not spread across test files.
const VIEWPORT = { width: 1280, height: 720 };
const ACCOUNT_SWITCHER_COORDS = { x: 123, y: 94 };
// Dropdown options render as a fixed-height list starting right below the switcher; "Atlas Scale
// Test" is this user's 3rd account (My account, Acorns Demo, Atlas Scale Test) → 3rd option slot.
const ACCOUNT_OPTION_COORDS = { x: 123, y: 222 };

// dashboardCategory (Zustand PersistStore, key "Akto-data" in sessionStorage) determines the
// x-context-source header every API request sends (see util/request.js), which in turn scopes
// server-side collection visibility (UsersCollectionsList.getContextCollectionsForUser). The UI's own
// category switcher (Headers.js handleDashboardChange) sets this then IMMEDIATELY calls
// window.location.reload() — racing Zustand's persist write against the reload, so the switch
// silently reverts on read-back (confirmed: after clicking through the UI, getAllCollectionsBasic
// still only returned the ~49 API-Security-scoped collections instead of the real ~25,890 ATLAS
// ones). Writing it directly into sessionStorage via addInitScript — which runs before any of the
// page's own JS on every navigation in this context — sidesteps that race entirely.
const REQUIRED_DASHBOARD_CATEGORY = 'Endpoint Security';

function fetchLatestRefreshToken(email) {
  const script = `
    const u = db.users.findOne({ login: ${JSON.stringify(email)} });
    if (!u || !u.refreshTokens || !u.refreshTokens.length) { print("__NO_TOKEN__"); quit(1); }
    print(u.refreshTokens[u.refreshTokens.length - 1]);
  `;
  const out = execSync(
    `docker exec mongo mongosh --quiet common --eval '${script.replace(/'/g, "'\\''")}'`,
    { encoding: 'utf8' },
  ).trim();
  if (!out || out.includes('__NO_TOKEN__')) {
    throw new Error(
      `No refreshToken found for ${email} in common.users. Log in once through the real UI to seed one.`,
    );
  }
  return out.split('\n').pop().trim();
}

module.exports = async function globalSetup() {
  const refreshToken = fetchLatestRefreshToken(LOGIN_EMAIL);

  const browser = await chromium.launch({ channel: 'chrome' });
  const context = await browser.newContext({ baseURL: BASE_URL, viewport: VIEWPORT });
  // The custom gzip storage adapter's getItem() falls back to plain JSON.parse when gzip-decompression
  // fails (see createGzipStorage in PersistStore.js) — so writing plain, uncompressed JSON here is
  // enough; no need to replicate the app's own pako.deflate + base64 encoding.
  await context.addInitScript((category) => {
    window.sessionStorage.setItem(
      'Akto-data',
      JSON.stringify({ state: { dashboardCategory: category }, version: 0 }),
    );
  }, REQUIRED_DASHBOARD_CATEGORY);
  await context.addCookies([
    {
      name: 'refreshToken',
      value: refreshToken,
      domain: 'localhost',
      path: '/dashboard',
      httpOnly: true,
      sameSite: 'Lax',
    },
  ]);

  const page = await context.newPage();
  // Any /dashboard/* route makes UserDetailsFilter mint a fresh access-token from the refreshToken
  // cookie and establish the server-side session (lands on "My account" by default).
  await page.goto(`${BASE_URL}/dashboard/observe/inventory`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('text=/Akto ATLAS|Inventory|Collections/i', { timeout: 20_000 });

  // Switch to the target account via the sidebar account switcher (top-left, below the logo).
  // Selecting an account may trigger a full page reload (same pattern as the dashboard-category
  // switcher in Headers.js), so poll window.ACTIVE_ACCOUNT afterward instead of a single fixed wait.
  await page.mouse.click(ACCOUNT_SWITCHER_COORDS.x, ACCOUNT_SWITCHER_COORDS.y);
  await page.waitForTimeout(500);
  await page.mouse.click(ACCOUNT_OPTION_COORDS.x, ACCOUNT_OPTION_COORDS.y);

  let activeAccount;
  for (let i = 0; i < 10; i++) {
    await page.waitForTimeout(1000);
    activeAccount = await page.evaluate(() => window.ACTIVE_ACCOUNT).catch(() => undefined);
    if (activeAccount && String(activeAccount) !== '1000000') break;
  }
  // eslint-disable-next-line no-console
  console.log(`[global-setup] authenticated as ${LOGIN_EMAIL}, active account: ${activeAccount}`);
  if (!activeAccount || String(activeAccount) === '1000000') {
    throw new Error(
      `Account switch to "${TEST_ACCOUNT_NAME}" didn't take effect (window.ACTIVE_ACCOUNT is still ` +
      `${activeAccount}). The dropdown option position may have changed — re-check ` +
      `ACCOUNT_OPTION_COORDS against a fresh screenshot of the open switcher.`,
    );
  }

  // dashboardCategory is already forced to "Endpoint Security" via the addInitScript above (every
  // navigation in this context, including the one that just happened) — no UI interaction needed, and
  // deliberately NOT using the real category-switcher dropdown (Headers.js's handleDashboardChange
  // races its own state-write against an immediate window.location.reload(), which silently reverted
  // the category on read-back when tried here).

  await context.storageState({ path: AUTH_FILE });
  await browser.close();
};
