// Measures and asserts an upper bound on load time for every page touched by this session's ATLAS
// perf work, so a regression back to "hangs forever" (the New Layout bug this suite exists to guard
// against — see agentic-assets-new-layout.spec.js and atlas-scale-test/DASHBOARD_OPTIMIZATION.md)
// shows up as a clear timing failure instead of a vague test timeout. Thresholds are generous relative
// to measured real-scale numbers (this account has ~25,890 collections) specifically to avoid
// flakiness — the goal is catching a 5-10x-plus regression, not micro-benchmarking.
const { test, expect } = require('./fixtures');

async function timed(fn) {
  const start = Date.now();
  await fn();
  return Date.now() - start;
}

const pageHeading = (page) => page.getByRole('heading', { name: 'Agentic assets', exact: true });

test.describe('Page load timing', () => {
  test('Inventory (Collections) loads within budget', async ({ page }) => {
    const ms = await timed(async () => {
      await page.goto('/dashboard/observe/inventory');
      // Heading label is category-specific (mapLabel in labelHelper.js) — ATLAS accounts show
      // "Agentic Collections" instead of the generic "API Collections".
      await expect(page.getByRole('heading', { name: /Collections$/ })).toBeVisible({ timeout: 30_000 });
    });
    console.log(`[timing] Inventory: ${ms}ms`);
    expect(ms).toBeLessThan(30_000);
  });

  test('Agentic Assets legacy page loads within budget', async ({ page }) => {
    const ms = await timed(async () => {
      await page.goto('/dashboard/observe/agentic-assets-legacy');
      await expect(pageHeading(page)).toBeVisible({ timeout: 30_000 });
    });
    console.log(`[timing] Agentic Assets legacy: ${ms}ms`);
    expect(ms).toBeLessThan(30_000);
  });

  test('New Layout grouped table loads within budget', async ({ page }) => {
    test.setTimeout(150_000);
    await page.goto('/dashboard/observe/agentic-assets-legacy');
    await expect(pageHeading(page)).toBeVisible({ timeout: 30_000 });

    const ms = await timed(async () => {
      await page.getByText('New Layout', { exact: false }).first().click();
      await expect(
        page.getByText('No Rows To Show', { exact: true }).or(page.locator('.ag-row').first()),
      ).toBeVisible({ timeout: 120_000 });
    });
    console.log(`[timing] New Layout grouped table: ${ms}ms`);
    // Real-scale grouping (~25,890 collections fanning out into ~795 overlapping groups) is a
    // legitimate ~30-50s synchronous computation in a dev build (see buildAgenticAssetsPageData's
    // groupsCache comment in constants.js, and DASHBOARD_OPTIMIZATION.md). 120s catches a return of
    // the "never completes" hang this spec was written to guard against without being flaky against
    // that baseline.
    expect(ms).toBeLessThan(120_000);
  });

  const NHI_PAGES = [
    { path: '/dashboard/nhi/identities', heading: 'Identities' },
    { path: '/dashboard/nhi/violations', heading: 'Violations' },
    { path: '/dashboard/nhi/policies', heading: 'Policies' },
  ];
  for (const { path, heading } of NHI_PAGES) {
    test(`NHI ${heading} page loads within budget`, async ({ page }) => {
      const ms = await timed(async () => {
        await page.goto(path);
        await expect(page.getByText(heading, { exact: false }).first()).toBeVisible({ timeout: 30_000 });
      });
      console.log(`[timing] NHI ${heading}: ${ms}ms`);
      expect(ms).toBeLessThan(30_000);
    });
  }
});
