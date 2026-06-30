// Module-level cache for test run execution status counts. Counts for COMPLETED runs are
// stable, so caching them here (instead of a component ref) keeps them across remounts and
// avoids re-hitting the heavy backend status API when revisiting the page within the TTL.
const RUN_STATUS_CACHE_TTL_MS = 5 * 60 * 1000;
const runStatusCache = new Map(); // testingRunResultSummaryHexId -> { counts, ts }

export function getCachedRunStatusCounts(hexId) {
  const entry = runStatusCache.get(hexId);
  if (!entry) return undefined;
  if (Date.now() - entry.ts > RUN_STATUS_CACHE_TTL_MS) {
    runStatusCache.delete(hexId);
    return undefined;
  }
  return entry.counts;
}

export function getValidRunStatusMap() {
  const result = {};
  const now = Date.now();
  runStatusCache.forEach((entry, hexId) => {
    if (now - entry.ts > RUN_STATUS_CACHE_TTL_MS) {
      runStatusCache.delete(hexId);
    } else {
      result[hexId] = entry.counts;
    }
  });
  return result;
}

export function setCachedRunStatusCounts(statusSummaries) {
  Object.entries(statusSummaries || {}).forEach(([hexId, counts]) => {
    runStatusCache.set(hexId, { counts, ts: Date.now() });
  });
}
