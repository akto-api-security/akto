const { test, expect } = require('./fixtures');
const { collectPageErrors, THIRD_PARTY_NOISE } = require('./helpers');

test('authenticated session loads the dashboard on the target account', async ({ page }) => {
  const errors = collectPageErrors(page, { ignoreUrls: THIRD_PARTY_NOISE });

  await page.goto('/dashboard/observe/inventory');
  // Heading label is category-specific (mapLabel in labelHelper.js) — ATLAS/Endpoint Security
  // accounts show "Agentic Collections" instead of the generic "API Collections".
  await expect(page.getByRole('heading', { name: /Collections$/ })).toBeVisible({ timeout: 15_000 });

  const activeAccount = await page.evaluate(() => window.ACTIVE_ACCOUNT);
  expect(activeAccount).toBeTruthy();

  // A handful of endpoints briefly 403 on first load right after an account switch, self-correcting
  // via retry within ~1-2s (see helpers.js) — give that window before checking for real failures.
  await page.waitForTimeout(3000);
  errors.assertClean();
});
