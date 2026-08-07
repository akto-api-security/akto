# E2E tests (Playwright)

Runs against a locally running dashboard (`./run-master.sh` from the repo root, `http://localhost:8080`
by default). Not wired into CI — this is a local dev tool for testing against real seeded data.

## Setup

```bash
npm install                 # installs @playwright/test (already a devDependency)
npm run test:e2e            # run the suite
npm run test:e2e:ui         # interactive UI mode
```

Uses the system-installed Google Chrome (`channel: 'chrome'` in playwright.config.js) instead of a
separate downloaded browser, since no other Chromium/Playwright browser is installed in this dev setup.

## Authentication

There's no test user with a real password — the seeded dev user (`rakshak@akto.io` by default,
override with `AKTO_TEST_EMAIL`) only has an AUTH0 signup entry, no password hash, so the real login
form can't be driven directly.

`global-setup.js` instead:
1. Reads that user's most recent `refreshToken` straight out of the local Mongo (`common.users`,
   via `docker exec mongo mongosh`) — the same token a real browser login would have left behind.
2. Injects it as a cookie and lets the app's own `UserDetailsFilter` mint a fresh access-token /
   session from it, the same way it would for a real returning session.
3. Switches to the target test account via the sidebar account switcher (default: "Atlas Scale
   Test", override with `AKTO_TEST_ACCOUNT_NAME`) — this user's default account (`user.findAnyAccountId()`,
   "My account") doesn't have role/API access configured and every ATLAS call 403s under it.
4. Saves the resulting storage state to `.auth/user.json` (gitignored), which every test file reuses
   via `playwright.config.js`'s `use.storageState` — individual specs never touch auth.

If tests start failing with "No refreshToken found" or 403s again, either that token expired/was
rotated, or the account setup changed — log into the real UI once as that user to reseed a token, or
re-check which account currently has ATLAS role access, and adjust `AKTO_TEST_EMAIL`/
`AKTO_TEST_ACCOUNT_NAME` accordingly.

**Coordinate-based clicks**: the account switcher dropdown didn't resolve through Playwright's
`getByRole`/`getByText` locators (tried both `button` and `combobox` roles against its own ARIA
snapshot — both timed out; it's a custom Polaris component rendered through a portal/overlay).
`global-setup.js` clicks it by fixed pixel coordinates instead, which is why the viewport is pinned to
1280×720. This is isolated to that one bootstrap step, not spread across test files.

## Env vars

| Var | Default | Purpose |
|---|---|---|
| `AKTO_BASE_URL` | `http://localhost:8080` | Dashboard base URL |
| `AKTO_TEST_EMAIL` | `rakshak@akto.io` | User whose refreshToken gets reused |
| `AKTO_TEST_ACCOUNT_NAME` | `Atlas Scale Test` | Account to switch into after login |
