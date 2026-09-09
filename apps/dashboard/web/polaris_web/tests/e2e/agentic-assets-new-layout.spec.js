// Regression coverage for a real bug found in this suite's own development: toggling "New Layout" on
// the Agentic Assets page threw `TypeError: analysisByKey.get is not a function` inside
// buildAgenticAssetsPageData (constants.js) on every single mount — a plain object was passed where a
// Map was expected. That exception fired synchronously inside the mount effect's try block, before any
// state was set, landing straight in the outer catch, which zeroed out every stat card and the table —
// indistinguishable from the page being permanently stuck/unresponsive, since nothing ever errored
// visibly and nothing ever rendered. Fixed by passing `new Map()` for the first (synchronous) render
// pass instead of `{}`. This spec exists so that regression can't silently come back.
const { test, expect } = require('./fixtures');
const { collectPageErrors, THIRD_PARTY_NOISE } = require('./helpers');

// "Agentic assets" as plain text matches 3+ elements on this page (the h1, a subdued sub-label, and
// the "No Agentic assets found" empty-state line) — the page heading role is the only unambiguous way
// to target it.
const pageHeading = (page) => page.getByRole('heading', { name: 'Agentic assets', exact: true });

test.describe('Agentic Assets — New Layout toggle', () => {
  test('legacy page loads cleanly', async ({ page }) => {
    const errors = collectPageErrors(page, { ignoreUrls: THIRD_PARTY_NOISE });

    await page.goto('/dashboard/observe/agentic-assets-legacy');
    await expect(pageHeading(page)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('New Layout')).toBeVisible();

    // A handful of endpoints briefly 403 on first load right after an account switch, self-correcting
    // via retry within ~1-2s (see helpers.js) — give that window before checking for real failures.
    await page.waitForTimeout(3000);
    errors.assertClean();
  });

  test('round-trips New Layout on and back off without crashing or hanging', async ({ page }) => {
    test.setTimeout(150_000);
    const errors = collectPageErrors(page, { ignoreUrls: THIRD_PARTY_NOISE });

    // Every test gets a fresh context restored from the static saved storageState, so
    // agenticNewLayout always starts false here regardless of what earlier tests did — start from
    // legacy explicitly rather than assuming any prior toggle state carried over.
    await page.goto('/dashboard/observe/agentic-assets-legacy');
    await expect(pageHeading(page)).toBeVisible({ timeout: 15_000 });

    await page.getByText('New Layout', { exact: false }).first().click();
    await expect(page).toHaveURL(/\/dashboard\/observe\/agentic-assets(\?|$)/, { timeout: 10_000 });

    // The page must reach a settled state (either real rows or the empty-state message) within a
    // bounded time — this is the actual regression guard. At the real ~25,890-collection scale this
    // account has, building the grouped table is a legitimate, if slow (~30-50s depending on whether
    // the shared Dashboard.jsx app-shell's own collections fetch is still warming up — see
    // buildAgenticAssetsPageData's groupsCache comment in constants.js), synchronous computation. The
    // bound here must stay well above that, but it must NOT hang indefinitely, which is what the crash
    // this spec guards against actually looked like (never resolving at all).
    await expect(
      page.getByText('No Rows To Show', { exact: true }).or(page.locator('.ag-row').first()),
    ).toBeVisible({ timeout: 90_000 });

    // The summary stat cards must show resolved content, not be stuck on a loading spinner.
    await expect(page.getByText('Agentic Assets').first()).toBeVisible();
    await expect(page.getByText('Violations').first()).toBeVisible();

    // Toggle back off — should return to the legacy page just as cleanly.
    await page.getByText('New Layout', { exact: false }).first().click();
    await expect(page).toHaveURL(/\/dashboard\/observe\/agentic-assets-legacy/, { timeout: 10_000 });
    await expect(pageHeading(page)).toBeVisible({ timeout: 15_000 });

    errors.assertClean();
  });
});
