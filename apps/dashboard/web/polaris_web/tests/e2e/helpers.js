// Shared helpers for the e2e suite — mainly console/network error collection, since "does this page
// load without silently failing" is the recurring question across most of these tests.

// Chrome auto-logs a "Failed to load resource: the server responded with a status of NNN ()" console
// message for every non-2xx response, duplicating what the `response` listener below already reports
// with an actual URL attached — drop it from the console-error signal so we don't double-count, and so
// filtering "did this URL eventually succeed" (see httpErrors below) has one authoritative source.
const CHROME_AUTO_LOGGED_HTTP_ERROR = /^Failed to load resource: the server responded with a status of/;

// AG Grid's license-warning banner logs its border rules as their own console.error calls, containing
// nothing but repeated `*` characters and whitespace — no shared substring with the worded lines to
// pattern-match against, so they need their own check.
const isAsteriskBorderLine = (text) => /^[*\s]+$/.test(text);

/**
 * Attaches listeners that collect console errors, uncaught page errors, and HTTP errors for the
 * lifetime of the page. Call errors.assertClean() at the end of a test to fail with a readable diff.
 *
 * HTTP errors are reported per-URL and only counted as a real failure if that URL's LAST response
 * during the test was also an error — this app has a known, pre-existing (not introduced by any work
 * in this repo's recent history) brief 403 on a handful of endpoints on first page load right after an
 * account switch, which a same-request retry resolves within ~1-2s. That's noise worth not failing
 * tests on; a URL that 403s and never subsequently succeeds is a real problem.
 */
function collectPageErrors(page, { ignoreUrls = [] } = {}) {
  const consoleErrors = [];
  const pageErrors = [];
  const responsesByUrl = new Map(); // url -> ordered list of status codes seen

  const isIgnored = (url) => ignoreUrls.some((pattern) => url.includes(pattern));

  page.on('console', (msg) => {
    if (msg.type() !== 'error') return;
    const text = msg.text();
    if (CHROME_AUTO_LOGGED_HTTP_ERROR.test(text)) return;
    if (isAsteriskBorderLine(text)) return;
    if (isIgnored(text)) return;
    consoleErrors.push(text);
  });
  page.on('pageerror', (err) => pageErrors.push(err.message));
  page.on('response', (res) => {
    const url = res.url();
    if (isIgnored(url)) return;
    if (!responsesByUrl.has(url)) responsesByUrl.set(url, []);
    responsesByUrl.get(url).push(res.status());
  });

  return {
    consoleErrors,
    pageErrors,
    get persistentHttpErrors() {
      const out = [];
      for (const [url, statuses] of responsesByUrl) {
        const last = statuses[statuses.length - 1];
        if (last >= 400) out.push(`${statuses.join(' -> ')} ${url}`);
      }
      return out;
    },
    assertClean() {
      if (pageErrors.length) {
        throw new Error(`Uncaught page error(s):\n${pageErrors.join('\n')}`);
      }
      if (consoleErrors.length) {
        throw new Error(`Console error(s):\n${consoleErrors.join('\n')}`);
      }
      const httpErrors = this.persistentHttpErrors;
      if (httpErrors.length) {
        throw new Error(`Request(s) that never succeeded:\n${httpErrors.join('\n')}`);
      }
    },
  };
}

// Noise present on every page load regardless of app behavior — third-party (ad-blockers, analytics,
// CSP report-only violations from vendor scripts) plus a couple of this-local-environment gaps (see
// entries below) — not actionable, would make every test flaky/noisy if not filtered.
const THIRD_PARTY_NOISE = [
  'mxpnl.com',
  'clarity.ms',
  'intercom.io',
  'intercomcdn.com',
  'gstatic.com/faviconV2',
  'getbeamer.com',
  'c.bing.com',
  // AG Grid Enterprise license warning — a real console.error from the vendor library in this dev
  // environment (no valid license key configured here), not an application bug. The grid still
  // renders and functions; this is purely a watermark/console-noise concern.
  'AG Grid Enterprise License',
  'ag-grid.com/licensing',
  'Invalid License Key',
  'license key is not valid',
  // com.akto.action.threat_detection.* actions proxy to the threat-detection-backend service
  // (THREAT_DETECTION_BACKEND_URL, see run-tbs.sh), which this local suite doesn't start (it needs its
  // own Kafka/Mongo/database-abstractor stack) — each action's catch block turns the resulting
  // connection failure into a 422, and every caller already handles that gracefully (empty violation
  // data, no crash or hang). Not an app bug; would need the full TBS stack running to exercise the real
  // success path. Add further threat_detection-backed endpoint names here if new ones surface.
  'fetchHostSeverityCounts',
  'fetchSuspectSampleData',
  'fetchAgenticViolationCountsByHost failed',
];

module.exports = { collectPageErrors, THIRD_PARTY_NOISE };
