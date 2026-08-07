// Smoke coverage for the NHI Governance pages, which were converted from client-side full-array
// fetches to server-side pagination + aggregates earlier in this project's history (see
// atlas-scale-test/DASHBOARD_OPTIMIZATION.md, "S3"). Catches wiring regressions (wrong response
// shape, a dropped field a component still expects) without asserting on specific row content, since
// the underlying data varies by account/date-range.
const { test, expect } = require('./fixtures');
const { collectPageErrors, THIRD_PARTY_NOISE } = require('./helpers');

const PAGES = [
  { path: '/dashboard/nhi/identities', heading: 'Identities' },
  { path: '/dashboard/nhi/violations', heading: 'Violations' },
  { path: '/dashboard/nhi/policies', heading: 'Policies' },
];

for (const { path, heading } of PAGES) {
  test(`${heading} page loads without console/network errors`, async ({ page }) => {
    const errors = collectPageErrors(page, { ignoreUrls: THIRD_PARTY_NOISE });

    await page.goto(path);
    await expect(page.getByText(heading, { exact: false }).first()).toBeVisible({ timeout: 15_000 });
    // Table region (or its empty state) should resolve, not spin forever.
    await page.waitForTimeout(3000);

    errors.assertClean();
  });
}
