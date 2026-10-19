# Traffic retention cleanup

## Purpose
Admin action on the "Advanced traffic filters" page: delete APIs whose last observed traffic is older
than a cutoff (3 months / 6 months / 1 year / custom days). Two routes:
- **Dry run** (`api/previewTrafficRetentionCleanup`) — synchronous, per-collection summary + total. Deletes nothing.
- **Delete** (`api/startTrafficRetentionCleanup`) — returns immediately; a background thread deletes.

Deactivated collections, demo collections, API groups and collections with no `hostName` are out of scope.
No Jobs framework / `JobsCron` — explicit product decision.

## Source of truth
Per API `(apiCollectionId, url, method)`: `max(lastSeen)` over its host-header STIs in `single_type_info`
(`responseCode=-1, isHeader=true, param="host"`, ANY subType). `lastSeen` is epoch **seconds**, written with `$max`
(monotonic), lags real traffic by up to ~30 min (`APICatalogSync.getDBUpdatesForParamsHybrid`, 1800s tolerance).

An API is a candidate iff it has >=1 host STI AND `max(lastSeen) < cutoff`. No host STI => unknown, never a candidate.
Only uppercase standard method names (not OTHER) are considered, because `deleteApisPerDao` matches method by enum name.

## Data flow (as built)
UI `TrafficRetentionCleanup.jsx` -> `TrafficRetentionAction` -> `TrafficRetentionService`
- preview: `fetchEligibleCollections()` -> per collection `SingleTypeInfoDao.fetchApisNotSeenSince` -> `TrafficRetentionPreview`
  (`{days, cutoffEpoch, complete, totalStaleApis, collections:[{apiCollectionId,name,state,staleApis,deletedApis,error}]}`).
- start: validate cutoff -> per-account in-memory guard -> single dedicated daemon thread ->
  per collection: fetch stale keys -> `Utils.deleteApisStiLast` in batches of 500 -> `infoAndAddToDb` per collection.
  Progress is visible only on the logs page; there is no status route.

## Decision log
| Decision | Choice | Status |
|---|---|---|
| Staleness source | max host-header STI `lastSeen`, no subType filter (`SingleTypeInfoDao.filterForHostHeaderAnySubType`) | verified (test: stale GENERIC + fresh IP_ADDRESS => not a candidate) |
| Delete order | `single_type_info` LAST (`Utils.deleteApisStiLast`) so a half-failed delete stays a candidate | verified (order + failure tests) |
| Existing `Utils.deleteApis` | unchanged order (STI first) for current callers | verified (regression test) |
| Failure semantics | failed collection => `FAILED`, `staleApis=null`, `complete=false`; never 0 | verified (tests + JSON shape test) |
| Cutoff | fixed in the dry-run response, passed back to delete, floor 30 days, cap 3650 days | verified (service + action tests) |
| Authz | `ADMIN_ACTIONS` (READ preview / READ_WRITE start), `collectionScope=ACCOUNT_WIDE`, separate routes for audit | verified (`TestRbacGuards`) |
| Timeout | aggregation `maxTime` 30s per collection (`STALE_API_QUERY_MAX_TIME_SECONDS`); a timeout throws and becomes a `FAILED` row | applied; the timeout itself is not exercised by a test (embedded Mongo cannot be made to time out cheaply) |
| Execution | sync preview; one dedicated daemon thread for delete, in-memory per-account guard | verified; accepted risks below |
| Delete enabled in UI only when dry run is complete and >0 | frontend; backend does not require a prior dry run | accepted (ADMIN-only) |

## Gotchas
- `SingleTypeInfoDao.filterForHostHeader` also requires `subType == GENERIC`. Hosts that classify as IP/numeric get another
  subType, so a GENERIC-only max can be stale for a live API. Do NOT reuse it here.
- `deleteApisPerDao` and raw `aggregate` bypass RBAC. The delete thread has no `Context.userId`, so it is account-wide;
  the preview request runs as the user. Both are ADMIN + `ACCOUNT_WIDE`, so they see the same collections.
- Replayed/uploaded old traffic (HAR/Postman) stamps `lastSeen` with the message time, so such APIs look stale.
- `deletedApis` counts keys sent to the delete (idempotent bulk write), not a verified row count.
- Logs carry collection name/id and counts only — never URLs.

## Accepted risks
- No crash recovery or persisted status: a restart mid-delete loses progress visibility. Re-running is safe (STI last).
- The guard is per JVM; two dashboard instances can run the same cleanup concurrently. Deletes are idempotent.
- One thread for the whole JVM: deletes from different accounts queue behind each other.
- Candidate keys for a collection are held in memory (volume stated as low); revisit with a streamed cursor if not.
- The cutoff comes from the client; the server enforces only the 30-day floor, not that a dry run happened first.

## Open work
- UI visibility is gated by `window.USER_NAME.endsWith("@akto.io")` (cosmetic, mirrors the two existing buttons). That is a
  hardcoded email in a frontend gate; decide between backend-only gating (all ADMINs see it) or a feature flag.
- Not exercised in a real browser / running dashboard yet (struts JSON `root`/`includeProperties` wiring, Polaris rendering).
- `explain()` of the aggregation on a large account; add a covering index only if needed.
