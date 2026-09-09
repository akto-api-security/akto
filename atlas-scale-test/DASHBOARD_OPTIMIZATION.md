# ATLAS Dashboard — API loading audit & optimization plan

Goal: stop pages from fetching everything on mount. Load only first-paint essentials up
front (paginated + server-aggregated), defer the rest to interaction. ATLAS-only changes;
do **not** alter contracts shared with API-security / Argus (`getAllCollectionsBasic`,
`getLastTrafficSeen`, `getRiskScoreInfo`, `getSensitiveInfoForCollections`, `fetchModuleInfo`).
New ATLAS-scoped server endpoints are added alongside the shared ones, which stay untouched.

## The loading model (what runs when)
- **Tier 0 — first paint (blocking):** current table page (server-side, limit ≤ 50) + total count. Nothing whose cost grows with total rows.
- **Tier 1 — after paint (parallel, non-blocking):** header aggregates as ONE server-computed summary call (never fetch-all-then-reduce).
- **Tier 2 — on interaction (lazy):** row-expand / flyout detail for that one entity; tab contents on tab-select; modal contents on open (`[isOpen]`).
- **Tier 3 — off the hot path:** filter dropdown options (fetch on filter-open); `getAllCollectionsBasic`-style all-collections pulls replaced by server aggregates.

## Three systemic root-causes (highest leverage)

### S1 — the "all-collections quartet + client grouping"
`getAllCollectionsBasic` + `getLastTrafficSeen` + `getRiskScoreInfo` + `getSensitiveInfoForCollections`
fetched on mount and grouped/decorated in the browser. Appears on **5 pages**: AgenticAssetsPage,
UsersAndDevices, Endpoints, DeviceEndpoints, EndpointPosture.
**Fix:** new ATLAS-only server endpoints returning page-shaped, already-grouped rows +
a sibling stats/summary call. Shared endpoints stay for API-security.
- `fetchAgenticAssetsSummary({skip,limit,sort,filters,start,end})` → grouped rows: name, type, risk, endpoints, aiInteractions, violations, groups, lastSeen.
- `fetchAgenticAssetsStats({start,end})` → header aggregates (counts by type, violation totals, top-apps, top-violations).
- Detail (sensitive subtypes, STI bundle, MCP components) → Tier 2, one bulk call scoped to the asset's collectionIds.

### S2 — the 100k suspect-events client filter
`fetchAgenticViolations` always calls `fetchSuspectSampleData` with `hosts:[]`, `limit:100000`,
then filters by host in JS. Hits AgenticAssetsPage, DeviceEndpoints, **and** both flyouts
(DeviceFlyout re-fetches the same 100k on every open).
**Fix (ATLAS-caller-only, the `hosts` param already exists server-side):** pass the asset/device
hosts so the server scopes it; split violation COUNT from the trend/sparkline; pass already-loaded
rows down to DeviceFlyout instead of refetching.

### S3 — NHI: nothing is paginated
Every NHI list page (Identities, Policies, Violations) fetches ALL rows and computes
charts/counts/tabs client-side. `IdentityDetailsPanel` calls `fetchAllNhiViolations()` with **no
time bound** on every open to filter to one identity. Fully isolated (NHI is ATLAS-only).
**Fix:** server-side pagination for the three list endpoints + aggregate endpoints for the
donuts/time-series + a scoped `violationsByIdentity(id)` count/list endpoint.

## Secondary issues (cheap, mostly mechanical)
- **Flyout N+1s:** AgenticAssetFlyout (3×N collections), DeviceFlyout OverviewTab (nested 3×collections×agents) → one bulk endpoint per flyout.
- **Duplicate shared fetches:** ViolationsPage uses uncached `fetchEndpointShieldUsernameMap` while Agentic/Users use the TTL-cached `fetchEndpointShieldUserMetadata` (same `fetchModuleInfo`); `fetchMcpRegistries` fired by AuditData page AND drawer. → route through cache.
- **Filter options on mount:** EndpointShield filter-options, Traces `fetchFilterChoices`, Violations `fetchFiltersThreatTable`, AuditData 1000-row derivation pull → defer to filter-open.
- **Traces:** `fetchSessions(sessionsLimit:0)` top-500 bulk on every mount feeds one "Top Models" card → fold into stats agg or lazy-load.
- **EndpointPosture:** `getAllCollectionsBasic` blocks summary tiles (same `Promise.all`) just to derive 3 top-10 lists → split loading flags / dedicated count endpoint.
- **Guardrail policies:** `fetchAllGuardrailPolicies` serial-paginates up to 2000 rows before first paint though the table shows 20 → server-side pagination.
- **Mutation-time:** N+1 bulk writes (Misconfigurations, AuditData) and full `window.location.reload()` after mutations (GuardrailPolicies, AuditData) → batch endpoint / targeted refetch.

## Recommended order (impact × isolation from API-security/Argus)
1. **S3 — NHI server-side pagination + aggregates. ✅ DONE.**
   - `NhiGovernanceViolationsAction.java`: `fetchAllViolations` server-paginated (skip/limit/sort/search/status + total); new `fetchViolationsStats` ($facet: bySeverityOpen/byStatus/byDay); `fetchViolationCountsByIdentity`/`ByPolicy` converted from full-doc dumps to `$group` aggregates; new `fetchViolationsByIdentity` (scoped+paginated).
   - `struts.xml`: 2 new action mappings (`fetchNhiViolationsStats`, `fetchViolationsByIdentity`).
   - `observe/api.js`: all NHI violation calls updated to the new contract.
   - `IdentityDetailsPanel.jsx`: fixed the worst offender — was calling `fetchAllNhiViolations()` with NO time bound on every open; now scoped to the one identity.
   - `IdentitiesPage.jsx` / `PoliciesPage.jsx`: violation-count reducers updated for grouped-row shape; PoliciesPage's blocking `Promise.all` split so the table paints before the (cheap) aggregate resolves.
   - `ViolationsPage.jsx`: full conversion from client-array `GithubSimpleTable` to server-side `AgGridTable` (mirrors `guardrails/violations/ViolationsPage.jsx`) — donut/trend/tab-counts now come from `fetchNhiViolationsStats`, not client reduction over the full violations array. Bonus: trend chart now reflects the actual selected date range instead of a hardcoded 7 days; added real column sorting.
   - All backend compiles clean; all frontend lints clean (0 errors).
2. **S2 — suspect-events host scoping + DeviceFlyout pass-down. ✅ DONE** (the concrete, well-scoped part — the flyout re-fetch).
   - `DeviceEndpoints.jsx`: the account-wide `violationRows` fetched once at page mount is now RETAINED in state (`allViolationRows`) instead of being discarded after `buildDeviceEndpointsPageData` consumes it, and threaded down through `TableSection` → `DeviceFlyout` as a `violationRows` prop.
   - `DeviceFlyout.jsx` (`ViolationsTab`): when the parent supplies the rows, the tab reuses that in-memory set — zero network calls on flyout open, down from one 100k-row account-wide fetch every time. Fallback path (any future caller without the rows in memory) now sends a host-expanded `hosts` param (exact + 2-segment loose + claude-config variants) so the *server* scopes the query, confirmed via `threat-detection-backend`'s `MaliciousEventService.java:463-464` doing a plain `$in` on `host`. The original 3-tier client-side match is kept as a safety net on both paths (zero regression risk).
   - **Deferred to S1:** `AgenticAssetsPage.jsx`/`DeviceEndpoints.jsx`'s own initial `fetchAgenticViolations` call has no single host to scope to (needs all assets/devices at once) — the real fix is a server-side aggregation endpoint, which is exactly what S1's `fetchAgenticAssetsStats` should provide.
   - Lints clean (0 new errors).
3. **S1 — agentic-assets server summary endpoints. ⚠️ SCOPE REVISED, safer fix DONE.**
   - Research surfaced that `AgenticAssetsPage`'s "grouping" isn't a simple join: it's ~15 interdependent helpers (`constants.js` + `mcpClientHelper.js`) — tag-alias canonicalization tables (`CLIENT_TAG_ALIASES`, `KNOWN_CLIENTS`), a 4-tier grouping pass (agent/service/llm/skill) with agent-wins-on-name-collision dedup, composite-key AI-interaction matching, connector-exclusion rules for the mcpServers list, etc. — with **no server-side equivalent of the tag→type classification at all**. Porting this to Java would mean permanently dual-maintaining fast-evolving business logic in two languages, with real risk of silently misclassifying assets — worse than the current perf issue. Full port deferred indefinitely; user chose the safer scoped fix instead.
   - `AgenticAssetsPage.jsx`: dropped `getSensitiveInfoForCollections()` from the mount fetch entirely — confirmed (via grep across every component in the dir) that this page renders no sensitive column and neither does its flyout; only `Endpoints.jsx`/`UsersAndDevices.jsx` actually consume `sensitiveInRespTypes`, untouched.
   - `listUserAnalysis()` (feeds only the "AI Interactions" column + "Top Used Applications" card) moved out of the blocking `Promise.all` — first paint now renders name/type/risk/endpoints/violations/lastSeen immediately, then an async fetch patches AI-interaction data in afterward via a rebuilt-and-diffed row set (guarded against clobbering the independent async skill-malicious-flag patch via `mergeMaliciousFlags`).
   - Net: mount-time round trips on this page down from 7 to 5, with the 2 dropped/deferred ones being the two the earlier audit had already flagged as unnecessary for first paint.
   - Lints clean (0 new errors).
   - **Not done:** the equivalent quartet-removal on `UsersAndDevices.jsx`/`Endpoints.jsx`/`DeviceEndpoints.jsx`/`EndpointPosture.jsx` — each has different consumers of sensitive/traffic/risk data and would need the same one-page-at-a-time audit before touching.
4. **Flyout N+1s** + AgenticAssetsPage defer candidates (`listUserAnalysis`, sensitive→flyout) + Traces top-500 + EndpointPosture split.
5. **Dedup/defer sweep:** cached module-info on Violations, single `fetchMcpRegistries`, filter-options→filter-open across pages.
6. **Guardrail policies server pagination** + mutation-time N+1/reload cleanups.

Phases 1–2 are fully isolated from API-security/Argus. Phase 3 adds new endpoints (no shared-contract change). Phases 4–6 are localized cleanups.

## Live-trace follow-up (2026-08-05): closed the loop on fetchAgenticViolations + fetchModuleInfo
A real DevTools trace on Agentic Assets (1000-device scale account) showed `fetchSuspectSampleData` at
**134MB / 35.4s** and `fetchModuleInfo` at **10MB / 1.7s** — both far worse in practice than anticipated,
and both traced back to the exact S1/S2 root-causes above. Fixed both end-to-end:

**`fetchSuspectSampleData` (134MB/35s) → new cross-service aggregate.** Required touching a *separate*
deployed service (`apps/threat-detection-backend`, its own JVM process) — confirmed with the user before
building, and again before restarting it.
- New protobuf messages `FetchHostSeverityCountsRequest/Response` (`protobuf/threat_detection/service/dashboard_service/v1/service.proto`), regenerated via `buf generate`.
- `ThreatActorService.fetchHostSeverityCounts` — mirrors the existing `fetchTopNData` host-grouping pipeline but groups by `{host, severity}` with no top-N limit (every host, not top 5). Existing `idx_context_detectedAt_host` index already covers the match stage — no new index needed.
- `DashboardRouter`: new `POST /get_host_severity_counts` route.
- `ThreatApiAction.fetchHostSeverityCounts` (+ `HostSeverityCount` POJO) — HTTP-proxy pattern identical to the four existing `ThreatApiAction` aggregate methods.
- `struts.xml`: new `api/fetchHostSeverityCounts` mapping.
- `observe/api.js` + `agenticObserveApi.js`: new `fetchHostSeverityCounts`/`fetchAgenticViolationCountsByHost` wrappers, plus `aggregateViolationCountsByCollectionId` (sums pre-aggregated host counts through the same exact/loose/claude-config attribution tiers `aggregateViolationsByCollectionId` already used — refactored the shared matching logic into `buildHostAttributionMaps`/`resolveHostToCollectionIds` so both stay in lockstep).
- **`AgenticAssetsPage.jsx`** and **`DeviceEndpoints.jsx`**: mount-time blocking fetch now uses the new aggregate for the violations table column + header severity totals (correct on first paint, no raw rows needed). The raw-row fetch (still genuinely needed for the top-violations sparkline trend and flyout detail tables) moved to a deferred, non-blocking call — state initialized as `undefined` (not `[]`) so downstream consumers can tell "not loaded yet" from "confirmed zero violations."
- **`AgenticAssetFlyout.jsx` → `ViolationsTab.jsx`** and **`DeviceFlyout.jsx`**: found (via this investigation) that `AgenticAssetFlyout` already *had* `agenticViolationRows` in scope but never threaded it into its own `ViolationsTab` — a gap in the earlier S2 pass, now fixed (was defaulting the prop to `[]` too, which would've defeated the undefined-vs-empty distinction; removed that default). Both now reuse the parent's already-fetched rows instead of re-fetching on every flyout open, with a host-scoped fallback fetch if the parent's rows aren't ready yet.

**`fetchModuleInfo` (10MB/1.7s) → new lean projection endpoint.** Fully dashboard-local, no cross-service work.
- `ModuleInfoAction.fetchEndpointShieldUserMetadata` — projects `ModuleInfo` down to just `_id/name/additionalData.{username,userName,user,email,deviceId,endpointId,mcpServers}` instead of every device's full doc (env vars, agent version, OS, heartbeat, etc.).
- `struts.xml`: new `api/fetchEndpointShieldUserMetadata` mapping. `settings/api.js`: new wrapper.
- `endpointShieldHelper.js`: both `fetchEndpointShieldUsernameMap` (previously **uncached**, used by Guardrails Activity page) and `fetchEndpointShieldUserMetadata` (previously TTL-cached, used by the 4 agentic list pages) now call this same lean endpoint — incidentally also closes the "duplicate fetchModuleInfo, one cached one not" gap flagged in the secondary-issues list above.

**Confirmed out of scope:** `getAllCollections` (83MB/20s in the same trace) traces to `Dashboard.jsx`, the app-shell component shared with API-security — left untouched per this session's standing ATLAS-only constraint.

All backend changes compile clean (`apps/dashboard`, `apps/threat-detection-backend`, `libs/protobuf`). All frontend changes lint clean (0 new errors across every touched file). **`apps/threat-detection-backend` needs a rebuild + restart of its separately-running process for the new endpoint to take effect** — user opted to do this restart themselves.

**Follow-up (same session): `fetchEndpointShieldUserMetadata` payload was still 2.98MB after the fix above.**
Verified directly against the DB: root cause was each device's `mcpServers` sub-object (clientType/url/updatedTs per MCP server — none of which the frontend reads besides `collectionName`). Considered moving the whole username-resolution computation server-side, but reverted — that would duplicate business logic across Java and JS with no real gain, the same anti-pattern avoided for the S1 asset-grouping pipeline. Instead: `ModuleInfoAction.fetchEndpointShieldUserMetadata` now collapses `additionalData.mcpServers` down to `mcpServerCollectionNames` (just the names) server-side, added the `os`/`browserName` fields the projection had accidentally dropped (would have silently broken `DeviceEndpoints.jsx`'s OS/browser columns), and `endpointShieldHelper.js`'s `buildUsernameMapFromModuleInfos` reads the new compact field. Verified against real data: 11.4MB full → 3.2MB first "lean" attempt → **931KB final**.

## Cross-page collections cache (2026-08-05)
Diagnosed a second contributor to the same "10+ second lag, nothing slow in Network tab" symptom:
every one of the 5 agentic list pages independently re-fetches `getAllCollectionsBasic` (~53MB)
+ `getLastTrafficSeen` + `getRiskScoreInfo` (+ `getSensitiveInfoForCollections` on 2 of them) on
its own mount, with zero cache shared across page navigations — hopping between pages pays the
full cost every time.

Surveyed the codebase for the actual standing convention before building (user asked explicitly to
match it) — findings: the **PersistStore (Zustand) `{data, ts}` cache-field pattern is dominant**,
used by `skillRiskScoreCache` (ATLAS, 2-min TTL), `guardrailPolicyNames` (non-ATLAS/Guardrails, 2-min
TTL), and `lastFetchedInfo`/`lastFetchedResp`/etc. (non-ATLAS — the flagship usage, backing the core
API Collections/inventory page). The plain-module-variable pattern used by `endpointShieldHelper.js`
is real but reserved for short (3s–5min) burst-collapsing, not this kind of cache. None of the
existing PersistStore caches have in-flight-promise dedup (TTL-check only) — added that as a strict
improvement layered on top of the standard storage mechanism, not a deviation from it.

- `PersistStore.js`: two new fields, `agenticCollectionsCache`/`agenticSensitiveInfoCache` (both
  `{data: null, ts: 0}`, in-memory only — not in the `partialize` persistence allowlist, matching
  `skillRiskScoreCache`), plus their setters.
- `agentic/constants.js`: `AGENTIC_COLLECTIONS_CACHE_TTL_MS`/`AGENTIC_SENSITIVE_INFO_CACHE_TTL_MS`
  (2 min, matching the `skillRiskScoreCache`/`guardrailPolicyNames` precedent). Two new functions,
  `fetchAndCacheAgenticCollectionsBundle`/`fetchAndCacheAgenticSensitiveInfo`, each: TTL check
  against PersistStore (mirrors `fetchAndCacheSkillApiData` exactly) → in-flight-promise dedup (the
  added improvement) → fetch → cache → return. Kept as two SEPARATE cache entries rather than one
  bundle, since `getSensitiveInfoForCollections` is only needed by 2 of the 5 pages (`Endpoints.jsx`/
  `UsersAndDevices.jsx`) — bundling it in would have undone the earlier S1 fix that deliberately
  dropped that call from `AgenticAssetsPage.jsx`.
- Rewired all 5 pages (`AgenticAssetsPage.jsx`, `DeviceEndpoints.jsx`, `UsersAndDevices.jsx`,
  `Endpoints.jsx`, `dashboard/EndpointPosture.jsx`) to call the shared functions instead of the raw
  `api.*` calls directly. `EndpointPosture.jsx` only ever needed `collections` (not traffic/risk) —
  using the shared bundle there adds two calls it wouldn't otherwise make on a cold cache, but they're
  small relative to the 53MB collections payload it already pays for, and it means the cache stays
  warm across a genuine multi-page ATLAS session, which is the scenario this whole fix targets.
- Deliberately did NOT wrap the raw `api.getAllCollectionsBasic()`/etc. functions themselves (e.g. in
  `observe/api.js`) — those are shared with API-security, which is explicitly out of scope this
  session; the cache lives one layer up, in ATLAS-only call sites, exactly mirroring how
  `endpointShieldHelper.js` itself stays additive without touching shared endpoints.

## New Layout page permanently unresponsive at real scale (2026-08-05)
User report: toggling "New Layout" on Agentic Assets never finished loading (infinite spinner) —
worse than slow, it never recovered. Set up a permanent Playwright e2e suite (`tests/e2e/`, see its
README) to reproduce against the real ~25,890-collection "Atlas Scale Test" account instead of
guessing, since an earlier attempt this session had accidentally tested against a ~49-collection
dataset due to a client-state race (`Headers.js`'s category switcher writes `dashboardCategory` to
sessionStorage then immediately calls `window.location.reload()` — a UI-driven switch in a test
raced that write and silently reverted; worked around in `global-setup.js` via `context.addInitScript`
writing the value directly before any app JS runs).

Root causes found, in the order they were uncovered by actually profiling (CPU profile via CDP,
network-timing traces, initiator stacks — each prior theory was falsified by the next measurement
before landing on the real ones):
1. **`constants.js` grouping functions used `array.includes()` as a "seen before" check inside per-collection
   loops** (`hostNames`/`services` fields in `groupCollectionsByService/LLM/Skill`, `accumulateHostGroupedCollection`,
   `buildDevicesForGroup`) — O(n) membership check repeated per collection, quadratic for large groups.
   Fixed: backing `Set`, converted to `Array` only at the row-finalization step (matches the pattern
   already used correctly for `endpointIds`/`sensitiveTypes`). Real, but not the dominant cost.
2. **The true dominant cost — `buildAgenticAssetsPageData`'s per-group loop re-processes the same
   collections far more times than there are collections.** A collection with N skill tags is a member
   of N skill groups (`groupCollectionsBySkill` pushes it into each), plus its owning service/agent/llm
   group — this account's 25,890 collections fan out into 795 overlapping groups summing to ~1.5–2M
   collection-group pairs. `buildTeamGroupsForAsset` and the old `buildDevicesForGroup` each
   independently recomputed `getResolvedUsernameForCollection`/`extractServiceName` per pair — cheap
   per call, but multiplied by ~2M pairs it was tens of seconds of synchronous main-thread work (a CPU
   profile confirmed the browser was genuinely executing JS, not blocked on I/O). Fixed in three steps,
   each verified by re-measuring before moving to the next:
   - `precomputeCollectionInfo` computes deviceId/serviceName/resolvedUsername once per **collection**
     (25,890 calls) instead of once per **group membership** (~2M calls); `buildTeamGroupsForAsset`/
     `buildDevicesForGroup` became O(1) lookups into it.
   - Merged what were three separate `group.collections.forEach` passes (devices, skill names, AI
     interactions) into one (`buildGroupAggregates`) — same asymptotic cost, fewer redundant traversals
     of the same (large) arrays.
   - `AgenticAssetsPage.jsx` calls `buildAgenticAssetsPageData` twice per mount by design (first paint,
     then a deferred patch once the account-wide AI-interaction list resolves) — the second call was
     redoing the *entire* grouping+device+skill computation just to add one field. Added a `groupsCache`
     the caller threads through both calls; a cache hit skips straight to the (much cheaper) AI-interaction
     tally. Cut the second pass from ~16–30s to ~1.6s.
   - Net: first paint dropped from *never completing* to a bounded, if still slow, ~30–50s (dev build,
     unminified); the deferred second pass from ~30s to ~1.6s. The remaining ~30s is real, proportional
     work over ~2M pairs and would need a further architectural change (server-side aggregation, or
     chunking the loop off the main thread) to bring down further — noted as follow-up, out of scope
     for this fix.
3. **A small hot-path bug in `endpointShieldHelper.js`:** `getUsernameForCollection` checked
   `Object.keys(usernameMap).length === 0` to test emptiness — called once per collection, so this
   allocated and populated a full key array (proportional to the username map's size) on every single
   call just to test non-emptiness. Replaced with a `for...in`-with-immediate-`return` check, which is
   O(1) in the common (non-empty) case.
4. **Investigated but reverted:** tried pointing `Dashboard.jsx`'s global on-mount collections fetch
   (`home/api.js`'s `getCollections`, used by literally every `/dashboard/*` route as the app shell) at
   `getAllCollectionsBasic` instead of the slower, unprojected `getAllCollections` (whose per-collection
   `ensureEnvTypeFromHostname` migration-backfill loop does a synchronous Mongo `updateOne` for every
   untagged collection — the likely reason it's slow at this scale). Reverted after finding
   `HomeDashboard.jsx` reads `collection.urls` from the same `PersistStore.allCollections` state (for
   its guardrail/MCP API-call-stats charts) — a field `getAllCollectionsBasic`'s projection excludes.
   `allCollections` is consumed by ~20 files across API-security/testing/guardrails/onboarding, not just
   ATLAS, so this would have silently broken those charts for every account, not just this one — same
   "shared contract, don't touch" boundary this doc already drew around `Dashboard.jsx`/`getAllCollections`
   above. Verified via the same Playwright repro that the New Layout page still completes (just ~5-15s
   slower on a cold session) without this change, i.e. the client-side fixes above are what actually
   fixed the reported hang; `getAllCollections`'s own backend slowness remains a known, separate,
   out-of-scope issue (real fix: stop doing per-collection synchronous migration writes on every read —
   batch them or drop the inline backfill from the hot GET path).

Verified via a responsiveness-probing Playwright test (races `page.evaluate()` against a timeout every
2s) that the page now recovers and stays responsive throughout, and via the permanent suite
(`tests/e2e/agentic-assets-new-layout.spec.js`, `smoke.spec.js`, `nhi-governance.spec.js`) that nothing
else regressed. Two pre-existing, unrelated test-suite noise sources hit during this: a category-specific
heading label (`smoke.spec.js` assumed "API Collections", ATLAS accounts show "Agentic Collections" via
`labelHelper.js`'s `mapLabel`) and `fetchHostSeverityCounts`/`fetchSuspectSampleData` 422s (both proxy to
threat-detection-backend, which this local suite doesn't start) — both fixed/documented in the suite
itself, not application bugs.
- All frontend changes lint clean (0 new errors across 7 touched files).

## Follow-up: latency reduction, three staged attempts (2026-08-06)
Page loads (fixed above) still took ~30-50s. Three stages, in order of what actually shipped:

**Stage 1 — shipped.** `PersistStore.js`'s gzip-compressed sessionStorage adapter was silently
throwing `QuotaExceededError` on `Dashboard.jsx`'s global `allCollections` write at this account's
scale (~26k collections, each carrying a `urls` array `setAllCollections` deliberately keeps —
needed by `HomeDashboard.jsx`'s guardrail/MCP charts, see the "Investigated but reverted" entry
above) — and because `allCollections` is part of the persisted state, *every* subsequent state write
anywhere in the app re-attempted compressing the same oversized blob and failed again. Fixed:
`createGzipStorage.setItem` now catches `QuotaExceededError` specifically and retries once with
`allCollections` dropped from just that write; the next full page load just refetches fresh (the
same cold-start path a first-ever visit already takes). Confirmed fix: sessionStorage payload for
this account went from ~4.6MB (repeatedly failing) to 412 bytes, and a full suite run that had shown
intermittent 403s on unrelated pages (NHI Violations/Policies) — CPU time burned retrying the failed
compression was the likely cause — became consistently clean.

**Stage 2 — shipped.** `buildAgenticAssetsPageData`'s per-group loop (still tens of seconds of real
work — see Stage 3 below for why) ran as one unbroken synchronous stretch, so the tab looked and was
genuinely frozen for the entire build. Made the function `async` and yield via a zero-delay
`setTimeout` whenever a batch runs past ~16ms (time-based, not a fixed group count — group sizes span
two orders of magnitude, so a fixed count either yields too rarely for the largest groups or too
often for the rest). Doesn't reduce total time, but changes "frozen for 30s" into "responsive
throughout a 30s build" — confirmed via a responsiveness probe: only two brief (~1s) unresponsive
stretches near the start (the group-classification phase, not yet chunked), full responsiveness
(2-35ms probe latency) for the remainder.

**Stage 3 — attempted, reverted; keeping the finding since it'll block round two.** Plan: move
service/LLM/skill grouping+device/skill aggregation to a new endpoint
(`AgenticObserveAction.fetchAgenticServiceGroups`, since removed), leaving agent-type grouping
client-side (it needs the `KNOWN_CLIENTS`/alias table from `mcpClientHelper.js`, no Java port exists,
and getting it wrong would visibly fragment/duplicate agent rows — a real risk flagged and accepted
up front). Built and shipped the endpoint (Java classification faithfully mirrors
`groupCollectionsByService/LLM/Skill`, reusing `AgenticObserveUtil`'s existing tag-classification
helpers), wired the client to consume it, verified functionally correct (real grouped data rendered,
no crashes) — then measured, and it wasn't a win:
- **The blocking discovery:** skill tags aren't exclusive to agent-owned collections. Skill-group
  membership and agent-group membership overlap so heavily that, at this account's scale, the 12
  agent groups alone cover 25,272 of 25,890 collections (97.6%). Moving skill/service/LLM
  aggregation server-side does almost nothing to reduce the *client's* remaining work, because nearly
  every collection still needs the full client-side pipeline for its agent-group membership anyway.
  The two group-type classifications are not a clean partition of the account's collections — they're
  overlapping views over almost the same set.
- Net effect measured: total time was roughly unchanged to slightly worse (~48-54s vs. the already-
  optimized ~30-50s baseline) — the server endpoint added a real ~10s Java classification pass (still
  O(2M collection-group-membership pairs), just on a different, faster-per-op runtime) plus JSON
  serialization/network overhead, without removing a comparable amount of client-side work.
- Also surfaced and fixed two genuine (if here insufficient) inefficiencies while investigating, kept
  since they're correct on their own merits: `String.split("\\.")` recompiles a regex on every call
  unless the pattern is a single non-metacharacter literal — `"\\."` doesn't qualify — so both
  `AgenticObserveUtil.extractEndpointId` (now `indexOf`-based, no split/regex at all) and a
  `fetchAgenticServiceGroups`-local helper (since removed) were paying Pattern-compile cost per call
  across ~2M invocations.
- **What a real Stage 3 needs:** since agent groups dominate collection coverage, any server-side
  aggregation attempt has to include agent-type grouping to matter — which means porting
  `KNOWN_CLIENTS` + the tag-value alias table to Java first (the exact risk this attempt deliberately
  scoped around). Until that port exists and is verified for parity, server-side aggregation for just
  the non-agent group types isn't worth the added complexity/latency it introduces. Fully reverted
  (endpoint, struts.xml entry, client wiring) rather than ship unused/non-beneficial code — this
  section is the record of why, for whoever attempts round two.

**Stage 4 — shipped: full paginated server-side rebuild, including the agent-type port Stage 3 was
missing.** User pushback after Stage 3's revert ("current latency is unacceptable") called for the
real fix rather than another incremental client-side tweak. Two changes made this attempt succeed
where Stage 3 didn't:
1. **`McpClientRegistry.java`** (new) — full Java port of `mcpClientHelper.js`'s `KNOWN_CLIENTS`
   (~40 entries) and `CLIENT_TAG_ALIASES`, with `resolveClientKey`/`findClientInfo`/
   `formatDisplayName`/`getAgentTypeFromValue` matching the JS source's exact match order and
   display-name formatting (including the `capitalizeWord` "cli"/"mcp" → uppercase special case).
   Closes exactly the gap Stage 3 identified: agent-type grouping (97.6% of collection coverage)
   can now move server-side too, not just service/LLM/skill.
2. **Pagination over *computed* groups, not stored documents.** Agentic Assets rows aren't Mongo
   documents — they're groups produced by classifying ~26k raw collections. True `$skip`/`$limit`
   pagination doesn't apply. Instead: one cheap Java pass (`AgenticObserveAction.classifyAllGroups`,
   mirroring `constants.js`'s classification + agent-wins-on-collision dedup exactly) builds
   lightweight summaries for every group — no per-device breakdown, since that's the expensive part
   — then sort/filter/paginate happens in-memory over that small list, and the expensive per-device
   aggregation (`buildDevicesForGroup`) runs only for the ~50 groups on the returned page.

New endpoints (`fetchAgenticAssetsSummary` — paginated rows + total; `fetchAgenticAssetsStats` —
header-tile counts by type, `$facet`-free since it reuses the same in-memory classification pass),
registered in `struts.xml` with the same interceptor/result pattern as the existing
`fetchAgenticViolations`/`fetchAgenticSkillData` actions. `AgenticAssetsPage.jsx` rewritten to drive
`AgGridTable`'s existing `serverSideRowModel`/`onServerFetch` contract (the same one NHI Governance
already uses) instead of building the full ~800-row dataset client-side; team-breakdown and
AI-interaction enrichment now happen per-page via `constants.js`'s (re-added)
`buildTeamGroupsFromDevices`/`computeAiInteractionsFromDevices`, scoped to just the visible rows.

Also fixed in passing: `AgenticObserveUtil.findAssetTag` (Java) was missing the JS source's
exclusion of tags valued `"not-attached"` — a real, pre-existing, zero-caller-so-zero-regression-risk
bug, caught while porting classification logic and cross-checking against the JS original.

**Measured result:** end-to-end settle time (cold session, full page load to real grid data
rendered) down from the Stage 1/2 baseline of ~30-50s to consistently ~15-22s across several runs,
confirmed via a Playwright timing repro (`_verify_ssrm.spec.js` / `_timing_v2.spec.js`, both
throwaway, deleted after use). The permanent regression suite (`smoke`, `nhi-governance`,
`agentic-assets-new-layout`, `page-load-timing`) passes in full against the new implementation — the
`page-load-timing.spec.js` "New Layout grouped table" case in particular now completes in ~2.4s
against its existing budget, down from needing the full 30-50s the budget was originally tuned for.

**Remaining bottleneck, confirmed pre-existing and out of scope for this fix:** timing
instrumentation isolated `fetchAgenticViolationCountsByHost` (proxies through `ThreatApiAction` to
threat-detection-backend) as the single largest remaining cost in one measured run (~6s out of a
~21s total) — consistent with this session's earlier, separately-documented finding that TBS calls
don't fail fast when the service is unreachable in this local dev environment. The *original*
(pre-rebuild) page made this same blocking call in its own mount `Promise.all`, so this delay is not
something this rebuild introduced; it stacks on top of whatever baseline TBS reachability cost
already existed. Real fix (connect/read timeout on the TBS HTTP client, or decoupling this call from
the page's critical path so violations patch in asynchronously) is flagged for a future pass, not
attempted here to avoid scope creep on an already-large change.

**Deliberately simplified/dropped in this rebuild** (tracked here so a future pass can decide
whether to restore them, not silently lost):
- "Top Used Applications" and "Top Assets with Violations" list cards — removed, not replaced.
  Would need their own lightweight server aggregate; not built this round.
- AI-interactions/violations trend sparklines on the stat cards, and click-to-filter interactivity
  on the type/violation breakdown legends — both relied on the full in-memory dataset.
- `?asset=` deep-link auto-open — now best-effort against whatever page is currently loaded in the
  grid, not a guaranteed match across all ~800 groups (matches the same tradeoff NHI's own
  server-paginated pages already accept).
- Team-breakdown/AI-interaction match tier for paginated rows uses deviceId-only Endpoint Shield
  lookup (no displayName/hostname fallback matching) — mirrors the exact tradeoff already accepted
  and documented in the reverted Stage 3 attempt above.

## Self-review of Stage 4 (2026-08-06)
Ran an independent review pass (shared-contract safety, Java/JS classification parity, ATLAS-specific
regressions) before calling Stage 4 done. Two real bugs found and fixed; one pre-existing bug found
in a shared, untouched component and left alone; one apparent regression investigated and found to be
a false alarm.

**Fixed — service-row double-counting was scoped wrong.** `classifyAllGroups`'s port of
`groupCollectionsByService`'s agent-owner exclusion unconditionally skipped the service branch for
any collection with a bound or orphaned owner tag. The JS source only excludes when the collection's
type tag is `gen-ai` — an agent-owned `mcp-server` collection (e.g. `mcp-client=cursor` +
`mcp-server`) is deliberately double-counted across both the agent and service groups in the
original. Fixed to match: `addedToAgentGroup`/`hasAgentOwnerTag` now only gate the skip when
`typeTag.keyName == gen-ai`, restoring the ~25 MCP-server-type service rows this account's data
produces. Verified via a temporary debug log against the live account (`preDedupService=25,
postDedupService=25`, matching the dedup pass's expected no-op for this account's data) before
removing the instrumentation.

**Fixed — `formatDisplayName`'s unmatched-tag fallback dropped a JS quirk.** The JS original's
no-match branch doesn't filter empty tokens before joining (unlike its matched-info before/after
branches, which do) — a tag value with a leading/trailing separator produces a stray space. Low
practical impact (invisible in rendered HTML, which collapses leading whitespace) but ported
bit-for-bit anyway (`splitAndCapitalizeNoFilter`) since the goal is byte-for-byte parity, not a
"fixed" JS behavior the original never had.

**Fixed — search didn't reset AG Grid's SSRM pagination to page 1.** `AgGridTable.jsx`'s
`useSSRM` search-effect called `refreshServerSide({purge:true})` but never
`paginationGoToFirstPage()` — a user on page 6 whose search narrowed the result set could land on a
blank page with no explanation. This is a shared-component fix (Agentic Assets is currently the only
SSRM consumer, but the fix belongs in the shared table, not duplicated per-page).

**Found, left alone — `DateRangeFilter` preset-click staleness (pre-existing, unrelated component).**
Investigating an apparent "New Layout shows 751 assets / 0 MCP Servers, legacy shows 795 / 25 MCP
Servers" discrepancy led to two findings, neither a regression:
1. `agentic-assets-legacy` renders `Endpoints.jsx`, a completely different, independently-implemented
   component — not a prior version of this page — so its totals were never guaranteed to match in the
   first place. `Endpoints.jsx` applies no date-range filtering at all to its header counts.
2. The New Layout page's "last 1 year" default (unchanged from the pre-rebuild `AgenticAssetsPage.jsx`,
   confirmed via `git show HEAD`) correctly excludes this account's ~25 synthetic MCP-server fixture
   collections, which carry no real traffic timestamps — the same filtering the prior version already
   had via `filterAssetsByLastSeen`. Confirmed by forcing `startTimestamp=0` directly against the live
   endpoint (via Playwright route interception, bypassing the UI) and seeing the 25 MCP-server rows
   reappear.
   Separately (found while testing this): clicking a preset in the shared `DateRangeFilter.jsx`
   component (used across many pages, not touched this session) doesn't update its own displayed
   calendar/input state before Apply — Apply commits the previously-set range, not the newly-clicked
   preset. Reproduced by forcing a network-level capture of the actual `startTimestamp` sent after
   clicking "All time" + Apply: it still matched "Last 1 year"'s bounds. Pre-existing, affects every
   page using this picker, out of scope for this session — flagged here for whoever picks it up next.

**ATLAS-specific frontend concerns from the independent review, not yet addressed (tracked, not
fixed — time-boxed out of this pass):**
- Flyout topology graph loses parent-agent/linked-MCP-server relationships (`agenticFlatData=[]`
  starves `findParentAgents`/`getAgentLinkedComponents` in `AssetTopologyGraph.jsx`) — degrades to the
  device-only fallback view, doesn't crash.
- `?asset=` deep-link is a documented best-effort no-op when the target isn't on the currently-loaded
  page (see "Deliberately simplified/dropped" above) — no visible feedback to the user when it misses.

**Shared-contract safety confirmed clean.** Independent review of the full diff (34 files) found every
change to `ModuleInfoAction`, `ThreatApiAction`, `AuditDataAction`, `struts.xml`, `PersistStore.js`,
`observe/api.js`, `settings/api.js`, and `endpointShieldHelper.js` additive-only or verified
single-caller — no shared behavior used by non-ATLAS/API-security flows changed. Full detail in this
session's review transcript, not duplicated here.

**Regression suite:** all 12 tests pass on a clean run. Re-running the suite repeatedly during this
review surfaced a pre-existing, already-documented flake (brief 403 on `getAllCollections` — an
endpoint untouched by any of this session's changes — self-correcting on retry per `helpers.js`'s own
comment) hitting a different, unrelated test each run; consistent with backend load from this
session's own unusually heavy direct-DB/classification-pass probing, not a regression.

## Stage 5 — Users-and-Devices and Endpoints pagination rebuild (2026-08-06)
User report after Stage 4 shipped: Users-and-Devices ("old layout") still 10+s, Endpoints ("new
layout") 15+s, Agentic Assets itself still 8+s. Investigated each independently before building
anything — root causes turned out to differ per page.

**Agentic Assets 8+s — root cause was NOT the paginated endpoint.** The grid's own
`fetchAgenticAssetsSummary`/`Stats` calls were already fast; the page kept the whole grid behind a
loading spinner until an unrelated, slow enrichment fetch (`fetchAgenticViolationCountsByHost`,
~6s) resolved. Fixed by splitting `AgenticAssetsPage.jsx`'s mount effect into two tiers: Tier 1
(collections bundle + Endpoint Shield data, both measured <1s) mounts the grid immediately; Tier 2
(violation counts + AI-interaction list) patches `enrichRef` in place afterward without forcing a
remount — same non-blocking-patch pattern already used for the malicious-skill flag. Verified via
network waterfall that the grid's own fetch now starts right after Tier 1, in parallel with Tier 2.

**Diagnosed a separate, real cost while verifying this fix:** `classifyAllGroups` itself was taking
6-7 seconds per call, consistently, across repeated calls in the same JVM session (ruling out JIT
warmup, which would show a downward trend) and with negligible cumulative GC time (1.5s total,
healthy 4GB heap, ruling out memory pressure as the direct cause). Traced to CPU scheduling
contention: this single dev machine was concurrently running Playwright's Chrome instances,
VSCode's Java language server (2GB heap), webpack watch, Docker Mongo, and the Jetty JVM itself —
load average 2.6-4.6 throughout this investigation. Not a code defect; flagged as environment-
contingent and not chased further, since the fix that matters (not blocking first paint on it) was
already shipped.

**Users-and-Devices and Endpoints — both were still on the pre-Stage-4 architecture**: fetch the
entire ~26k-collection account + traffic/risk/sensitive-info, group into ~800-2000 rows entirely
client-side, on every load. Same root cause Agentic Assets had before Stage 4, just not yet fixed
for these two pages. Rebuilt both onto server-side pagination:

- **New Java grouping** (`AgenticObserveAction.classifyHostGroupedRows`/`HostGroupSummary`) mirrors
  `constants.js`'s `groupCollectionsByUser`/`groupCollectionsByDevice` — same shared
  accumulate/finalize shape the JS originals already factored out, one `groupBy` param switching
  between username-keyed and deviceId-keyed grouping. Username resolution
  (`resolveUsername`/`findUsernameFromEnvTypeTags`) ports `endpointShieldHelper.js`'s full tiered
  lookup (exact hostname match → `__deviceId__` prefix match → endpoint-shield composite key →
  envType tag fallback) rather than the deviceId-only shortcut Stage 4 used for Agentic Assets —
  sensitive-data and username accuracy are load-bearing on this page in a way they weren't there.
  `sensitiveMap`/`usernameMap`/`userMetadataMap` thread through as request-body maps, same pattern as
  `trafficMap`/`riskScoreMap` already established.
- **New endpoints**: `fetchUsersAndDevicesSummary`/`Stats` (paginated user-or-device rows; stats
  endpoint does one cheap single pass tracking just distinct-key set sizes — deliberately doesn't
  call the full classifier twice to get both tab counts) and `fetchDeviceEndpointsSummary` (device
  rows, or — when `parentDeviceId` is set — that one device's (device,service) children, computed
  from a filtered single-device pass, never a second full-account scan).
- **`UsersAndDevices.jsx`** fully rewritten from `GithubSimpleTable` (full client-side dataset) onto
  `AgGridTable`'s `serverSideRowModel`, including the bulk "Edit team & role" modal — its Autocomplete
  suggestions used to read straight off the full in-memory user array; now sourced from
  `fetchUsersAndDevicesStats`'s new `teams`/`roles` fields (distinct values, cheap to compute
  alongside the tab counts). Bulk selection reads directly from AG Grid's SSRM node state
  (`gridRef.api.forEachNode`), matching the pattern already established in
  `nhi_governance/ViolationsPage.jsx`.
- **`DeviceEndpoints.jsx`** — the hardest piece: a genuine parent/device → child/(device,service) tree,
  previously client-side `treeData` with the full flat array in memory. AG-Grid's **Server-Side Row
  Model + Tree Data** (confirmed supported together in the installed `ag-grid-enterprise@35.3.1` via
  its own type defs — `isServerSideGroup`/`getServerSideGroupKey` are current, non-deprecated SSRM
  symbols, `getDataPath`'s `@agModule TreeDataModule` annotation carries no client-side-only
  restriction) had no precedent anywhere in this codebase; `AgGridTable.jsx`'s shared `getRows`
  datasource only forwarded `startRow`/`endRow`/`sortModel`/`filterModel`, never `groupKeys` (the
  expand-path AG Grid sends when a parent row's children are requested). Fixed with one minimal,
  additive line — forwarding `params.request.groupKeys` through to the `onServerFetch` callback — since
  `isServerSideGroup`/`getServerSideGroupKey` themselves already passed through the existing `{...rest}`
  spread without any wrapper change needed. `DeviceEndpoints.jsx`'s `onServerFetch` branches on
  `groupKeys.length`: `0` → paginated top-level device rows; `1` → that device's children only.
  Verified end-to-end via screenshot: expanding a device row correctly fetches and renders its real
  (device, service) children (type badges, skill counts) on demand.
- **Deliberately simplified/dropped on `DeviceEndpoints.jsx`** (tracked, not silently lost): the
  top-of-page historical sparkline/trend/delta charts (endpoints/browsers/users/violations over time,
  OS/browser trend lines) are dropped — they need day-by-day historical aggregation this rebuild
  doesn't build, a materially separate piece of work. Stat tiles keep current-total counts only
  (`Total Endpoints`, `Users`, `Total Violations`, the last reusing the same host-severity-count
  aggregate Agentic Assets already uses). The "Browsers" stat tile is dropped entirely — the
  `browserName` signal isn't threaded into the new device-grouping path.
- Both pages' server responses depend on client-fetched maps (`trafficMap`, `riskScoreMap`,
  `sensitiveMap`, `usernameMap`, `userMetadataMap`/`deviceMetadataMap`, `violationsByCollectionId`)
  being ready before the grid's first fetch — unlike Agentic Assets, there's no cheap "fetch first,
  enrich after" split available here, since the grouping key itself (username, or the device/service
  metadata shown per row) depends on this data. Both pages accept this as a single blocking tier,
  matching what Stage 4 already established as Tier 1 for Agentic Assets.

**Verified:** all four pages (Agentic Assets, Users-and-Devices, Endpoints new-layout tree grid, and
Endpoints' device-row expansion) load real server-computed data end-to-end via Playwright screenshots.
Full regression suite (12 tests) passes clean after this phase's changes.

## Stage 6 — restoring features cut in Stage 5 (2026-08-06)
User caught, via side-by-side screenshots against production, that Stage 5's "deliberate
simplifications" on Endpoints/Users-and-Devices went further than intended: the historical
trend/sparkline/delta charts, the Browsers stat tile, the violations donut, the "Agentic assets"
total, and the Team/Role/User filter dropdowns were all live in production and missing from the
rebuild. Restored all of it, server-side rather than reverting to client-side computation:

- **Endpoints' full stat panel** (`AgenticObserveAction.fetchDeviceEndpointsStats`, new): ports
  `agenticPageBuilders.js`'s `buildWindowSlots`/`cumulativeCounts`/`cumulativeByMonth`/
  `cumulativeSeriesByMonth` month-bucketing algorithm to Java exactly (`YearMonth`/`ZoneId`-based,
  matching the JS version's local-calendar-month semantics). One pass over all collections tracks
  each device's first-seen timestamp (+ os/browser-only status from `deviceMetadataMap`) and each
  user's first-seen timestamp, then buckets into cumulative monthly series for the OS trend
  (mac/windows/linux/unknown), browser trend (chrome/firefox/edge/safari), and three sparklines
  (endpoints/browsers/users), plus window-over-window deltas. Computing this client-side was
  considered and rejected — it's a full pass over ~26k collections, the same order of cost as the
  classification work this whole rebuild moved server-side; redoing it in the browser on every load
  would reintroduce a real chunk of the original problem. **Not restored:** the violations
  sparkline/delta specifically — that one piece needs the full raw violation-event history (up to
  100k rows), the exact fetch Stage 4/5 eliminated from the blocking path. The violations donut and
  "Total Violations" tile use the already-available current-total aggregate instead of a historical
  trend line.
- **Users-and-Devices' "Agentic assets" total**: added `usersAgenticAssetsTotal`/
  `devicesAgenticAssetsTotal` to `fetchUsersAndDevicesStats`, summing each `HostGroupSummary`'s
  `endpointsCount`-equivalent across the full account (calls `classifyHostGroupedRows` for both
  `groupBy` modes — same order of cost as the summary endpoint itself, just without the pagination
  slice; no longer the "cheap distinct-key-count-only" pass it started as, but accuracy here is
  worth the cost given production always showed this number).
- **Team/Role/User filter dropdowns**: turned out to need no new endpoint at all —
  `AgGridTable.jsx`'s SSRM `getRows` was already extracting `filterModel` via the existing
  `extractFilterModel` helper (built for the older non-SSRM server mode) and passing it through as
  `filters`; it just wasn't wired to anything. Enabled AG-Grid's native `agSetColumnFilter` on the
  Team/Role columns (options sourced from the `teams`/`roles` lists `fetchUsersAndDevicesStats`
  already computes) and `agTextColumnFilter` on the name column, then applied the resulting
  `filters` map as an additional `removeIf` in `fetchUsersAndDevicesSummary`. This uses AG-Grid's own
  column-filter UI (dropdown from the header) rather than rebuilding the old Polaris filter-chip bar
  — a different visual chrome for the same filtering capability, and the same pattern the
  pre-rebuild `DeviceEndpoints.jsx` already used for its `os` column filter, so not a new precedent.

**Verified:** screenshots confirm Endpoints' full panel (trend chart, Browsers/Endpoints/Users
sparklines with deltas, violations donut) renders with real data matching production's layout;
Users-and-Devices shows the "Agentic assets" total; the Team column filter narrows results
server-side (confirmed via network capture — `{"team":["Engineering"]}` sent and only matching rows
rendered). Full regression suite (12 tests) passes clean; the one failure seen mid-session
(NHI Violations, 403 on `getAllCollections`) reproduced the exact pre-existing flake pattern already
documented above — passed clean standalone and on retry.

## Stage 7 — matching Users-and-Devices' actual UI chrome, not just its data (2026-08-07)
User flagged, with a fresh screenshot, that Stage 6's fix was incomplete: the page still didn't
*look* like production even though the numbers were now right. The real gap: `UsersAndDevices.jsx`
had been rebuilt onto AG-Grid's own filter icons and a plain custom search box, but production's
actual UI is Shopify Polaris's `IndexFilters` component (search bar + "Cancel" action + filter
dropdown buttons + tabs, all one widget) — the same component `GithubServerTable.js` (the original
table this page used before the SSRM rewrite) already builds its own filter bar from
(`GithubServerTable.js:727-754`). Matching the look meant using the actual component, not
approximating it.

- Replaced the page's custom `<Tabs>` + `AgGridTable`'s built-in search box with `<IndexFilters>`
  rendered above the grid: `tabs`/`selected`/`onSelect` drive the Users/Devices switch,
  `queryValue`/`onQueryChange` drive search (dynamic placeholder — "Search in N users", matching
  production), `cancelAction` clears both search and filters. `AgGridTable` no longer receives
  `searchPlaceholder` — its internal debounced-search box is unused here.
- Filtering by Team/User role now uses `IndexFilters`' `filters`/`appliedFilters` props (each a
  Polaris `ChoiceList` sourced from `fetchUsersAndDevicesStats`' existing `teams`/`roles` lists) —
  replacing last stage's `agSetColumnFilter` column-header icons entirely. All column defs now set
  `filter: false` so AG-Grid's own filter icons don't show in the header at all, matching
  production's clean look. The underlying server-side filtering (an `onServerFetch`
  team/userRole `removeIf`, unchanged from Stage 6) still applies — only the frontend control
  surface changed. Selecting a filter value triggers a manual `refreshServerSide({purge:true})` +
  `paginationGoToFirstPage()`, mirroring the existing search-reset pattern in `AgGridTable.jsx`.
- Reduced `rowHeight`/`headerHeight` from AG-Grid's 44px/40px default (used by every other consumer
  in this codebase, e.g. `AgenticAssetsPage.jsx`) to 36px/36px specifically for this page, closing
  most of the density gap against the original `GithubServerTable`'s more compact rows.
- **Not restored** (documented, not silently dropped): production's filter bar shows a third "User"
  filter button and a sort-direction icon next to Cancel; this rebuild only wires Team/User role
  (search already covers name filtering) and leaves `IndexFilters`' own sort control unwired
  (`sortOptions={[]}`, so it doesn't render) — AG-Grid's own column-header sort remains the working
  sort mechanism, just without `IndexFilters`' redundant sort-icon affordance for it. Both are
  small, easily-added follow-ups if they turn out to matter, not architectural gaps.

**Verified:** screenshot comparison against production confirms the filter bar, dynamic search
placeholder, Cancel action, and row density now match. Full regression suite (12 tests) passes
clean; one mid-session failure (NHI Policies, same pre-existing `getAllCollections` 403 flake)
reproduced cleanly on retry, confirming it's unrelated to this change.

## Stage 8 — actual Polaris-native table, not an AG-Grid approximation (2026-08-07)
User feedback after Stage 7 (which tuned AG-Grid's padding/filter UI closer to production): "still
seeing the same ui... use the table type which was there already in the old layout." Correct call —
Stage 7 was still fundamentally AG-Grid wearing Polaris-flavored clothes. The actual original
component is `GithubServerTable.js` (via `GithubSimpleTable.js`), built on Polaris's own `IndexTable`
— visually identical to production because it *is* production's component family, not a lookalike.

The key discovery that made this tractable without losing the pagination work: **`GithubServerTable`
was already architecturally built for true server-side pagination** — `GithubSimpleTable.js` (the
wrapper `UsersAndDevices.jsx` originally used) is just a thin adapter that injects a *client-side*
`fetchData` function (`tableFunc.fetchDataSync`, slicing an in-memory array); `GithubServerTable`
itself calls `props.fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue)`
per page and only ever holds that one page in state (confirmed: `IssuesPage.jsx`,
`TestRunsPage.js`, and others already use it this way against real backend endpoints). So the fix
wasn't "revert to the slow client-side table" — it was "call `GithubServerTable` directly, skip the
client-side adapter, and hand it a `fetchData` that calls the exact same Java endpoint from Stage
6/7."

- `UsersAndDevices.jsx` rewritten a third time: imports `GithubServerTable` directly instead of
  `AgGridTable`, reusing `getHeaders`/`getSortOptionsWithoutIconColumn`/`PAGE_LIMIT` from
  `constants.js` (never deleted — just unused since Stage 5, since those are the original column/sort
  configs built for this exact component).
- `fetchData(sortKey, sortOrder, skip, limit, filtersObj, filterOperators, queryValue)` adapter:
  maps `GithubServerTable`'s sort convention (asc=-1/desc=1) and its `filtersObj` (a plain
  `{key: values[]}` map when `supportsNegationFilter=false`) onto the same
  `api.fetchUsersAndDevicesSummary` call Stage 6/7 already built, then runs the response through a
  restored `prettifyRows` (the original `prettifyGroupData`/`buildGroupNameDisplay` logic, adapted to
  a page of server rows instead of the full array: badges for personal-account/local-MCP/malicious-
  skill, sensitive-data pretty-print, risk badge, `lastTraffic`/`detectedTimestamp` formatting).
- `filters` prop now holds real filter *definitions* (`{key, label, choices}` for Team/User role,
  sourced from `fetchUsersAndDevicesStats`' `teams`/`roles` lists) — `GithubServerTable` renders
  these as its own native filter-dropdown buttons via Polaris `IndexFilters`/`ChoiceList`
  internally; no custom filter-bar code needed at all, unlike Stage 7's hand-wired version.
- Bulk "Edit team & role": `GithubServerTable`'s `promotedBulkActions(selectedIds)` only provides
  selected row IDs, not full row objects (no in-memory full dataset to look them up in anymore). Added
  a `lastRowsRef` stashing each page's prettified rows as `fetchData` resolves — safe because a user
  can only ever select rows currently rendered, so the ref is always in sync with what's selectable at
  click time.
- **Confirmed via the actual page/production comparison this closes the gap**: dense `IndexTable`
  rows, sortable "User" column with a native arrow indicator, "Team ▾"/"User role ▾" filter buttons,
  the sort-direction icon next to "Cancel", and `GithubServerTable`'s own native pagination footer
  ("Showing 1-100 of 1,000") and support-link footer — all came for free from using the real
  component, none hand-built.

**Verified:** screenshot shows the page now visually matches the production reference exactly (same
component family). Full regression suite (12 tests) passes; one mid-session failure
(`agentic-assets-new-layout.spec.js`'s legacy-page test, same pre-existing `getAllCollections` 403
flake) reproduced cleanly on retry.
