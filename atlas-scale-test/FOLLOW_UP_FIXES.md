# ATLAS rebuild — follow-up fixes (deferred from PR #6002)

Findings from the PR #6002 code review (8-angle static review + live cross-environment
verification) that are **not yet fixed**. Everything below is intentionally left for a separate
PR. Ordered by severity; each item has enough detail (file:line, root cause, suggested fix) to
pick up cold.

## Already fixed on this branch (not repeated below)

- `DeviceEndpoints.jsx` Group/Role/Last-Traffic field-name mismatch, plus two related
  dead/redundant-state cleanups in the same file.
- Endpoints page's Total Violations stat card was missing its trend sparkline/delta entirely —
  restored by extending `FetchHostSeverityCountsRequest`/`Response` (proto) with month-bucketed
  totals via a cheap `$bucket` aggregation in threat-detection-backend, no raw-event fetch needed.
- `classifyAllGroups`'s "not-attached" hostname check ran before skill fan-out, undercounting
  Skills by excluding orphan-hostname collections entirely (Agentic Assets new layout reported
  782/Skills:745 vs legacy/prod's 795/Skills:758 for the same account) — moved the check to only
  gate the agent/service/llm branches, matching `groupCollectionsBySkill`'s reference behavior.
- `getTypeFromCollection`'s missing NOT_ATTACHED exclusion for `hasAiAgent` (was listed below as
  unfixed; folded into the skill-undercount fix above since both touch the same classification
  path).
- Agentic Assets' missing sparklines/deltas on the two main stat cards, and its two entirely
  missing "Top Used Applications"/"Top Assets with Violations" cards — restored server-side using
  data already fetched at mount (no new API calls, no per-collection payload growth). Verified
  live against Acorns Demo: numbers match production exactly (795/+56 assets, 617/+1 violations,
  identical top-5 violations ranking).
- "Top Assets with Violations" was missing its per-asset trend mini-chart (prod shows one next to
  each row, not just the number) — extended `FetchHostSeverityCountsRequest` with an optional
  `host_filter` so the same cheap `$bucket` aggregation can be scoped to one asset's own hostNames,
  one small targeted call per top-5 row.
- "Top Used Applications" showed "No AI interaction data yet." even against a real production data
  dump with genuine `UserAnalysisData` records — **initially misdiagnosed as a data-seeding gap;
  it wasn't.** Root cause: `UserAnalysisData.serviceId` is a readable client name ("claudecli",
  "codexcli", ...) matching the second segment of the collection's own hostname
  (`<deviceId>.<serviceId>.<host>`), not `module_info`'s own UUID `_id` — the account-wide matching
  originally used `module_info.id` as serviceId, which never appears in `UserAnalysisData` at all,
  so it silently matched nothing regardless of real data volume. Confirmed against origin/master's
  still-current (unmigrated) `analysisKeysForCollection`, which derives serviceId from hostname
  segments, not from `module_info`. Fixed by deriving the same hostname-segment candidates
  server-side from each group's own `hostNames`. Verified live: matches production exactly (Claude
  CLI 82.1K, razorpay-home 55.8K, etc.).
- Agentic Assets table's per-row "AI Interactions" column had the exact same `module_info`-vs-
  hostname-segment bug (was always "-", including for assets already proven to have real
  interaction data via the fixed card above). Moved the computation server-side —
  `buildDevicesForGroup` already iterates each collection's `hostName`, so it now derives the same
  hostname-segment candidates there and looks them up in `userAnalysisFlatMap`, accumulating per
  device; `shapeRow` just sums `devices[].aiInteractions`. Dropped the input/output token tooltip
  breakdown in the process (server only tracks totals per device) — small, deliberate scope cut.
  Verified live: Claude CLI's row shows 82.1K, matching the card; several skill rows show real
  counts while others correctly show "-" where no matching data exists.
- AI-Agent asset flyout's Components tab was missing MCP Server rows entirely — e.g. "Cursor"
  showed 25 Skill rows and zero MCP Servers, despite its Overview tab's own dependency graph
  showing 8. Root cause: `AgentComponentsView.jsx`'s `connectedMcps` reads `asset.mcpServers`, a
  field the rebuilt server-side `GroupSummary` (`AgenticObserveAction.java`) never populated
  anywhere (confirmed via grep — zero occurrences before this fix), unlike master's still-current
  client-side `constants.js`, which derives it from each agent's own non-connector-ingested
  collection hostnames via `extractServiceName`. Fixed by adding a `serviceNames` set to
  `GroupSummary`, populated in `accumulateCheap` (agent rows only) using the already-existing
  `isConnectorIngested`/`extractServiceNameForGrouping` helpers, and emitted as `mcpServers` in
  `toSummaryResponse()`. No new Mongo queries or API calls — `shapeRow`'s existing `...row` spread
  carries it straight through to the flyout. Verified live: Cursor's Components tab now lists all
  8 MCP Server rows (default, api.githubcopilot.com, localhost:9876, test,
  ai-security-docs.akto.io, mcp.razorpay.com, notion-mcp, server-github) alongside its 20 skills.
  Related gap (`agenticFlatData={[]}` breaking the MCP-server tools drill-down) fixed separately,
  see below.
- Every asset flyout's Devices tab showed "-" for User and Last Seen on every single row, for
  every asset type — not just Cursor. Same bug class as the mcpServers fix above: the rebuilt
  server-side `AgenticObserveAction.buildDevicesForGroup`/`DeviceAcc.toResponse()` only sends
  `deviceId`/`riskScore`/`lastSeenEpoch`/`services`/`aiInteractions` per device, never `username`
  or a formatted `lastSeen` string, while `AgenticAssetFlyout.jsx`'s `DevicesTab` (unchanged since
  master) reads both straight off each device row. Master's client-side `buildDevicesForGroup`
  (`constants.js`) always populated both via `getResolvedUsernameForCollection`/
  `func.formatChatTimestamp`. The account's Endpoint Shield `usernameMap` was already fetched
  client-side and used for the aggregate team-count rollup (`buildTeamGroupsFromDevices`), just
  never attached to the individual device rows `DevicesTab` renders. Fixed by adding
  `enrichDevicesWithUsername` (`constants.js`), reusing `buildTeamGroupsFromDevices`'s own
  deviceId-keyed `usernameMap` lookup tier, wired into `shapeRow` in `AgenticAssetsPage.jsx`.
  Verified live: Cursor's Devices tab now shows real usernames (rakshaksatsangi, jane.doe,
  john.smith, liam.patel) and real Last Seen values (11 days ago, 1 week ago) instead of "-"
  across the board. Risk Score is still "-" for all 4 of Cursor's devices — left as-is since the
  field genuinely is populated by `DeviceAcc.toResponse()` (only nulled when `riskScore <= 0`), so
  this looks like real zero-risk data for this asset's devices rather than a missing-field bug;
  worth a second look if a different asset's devices turn out to have real risk scores but still
  show "-".
- MCP-server tools drill-down (click an MCP Server component inside an AI Agent's Components tab
  to see its own tools) always showed "No tools found.", for every server, every agent. Root
  cause: `AgentMcpToolsView` (`AgentComponentsView.jsx`) looked up the clicked server's
  `collectionIds` via `agenticFlatData.find(a => a.name === selectedMcp.name)`, but
  `AgenticAssetsPage.jsx` hardcodes `agenticFlatData={[]}` in the rebuilt architecture (the
  account-wide flat array master's client-side pipeline built is deliberately not sent), so the
  lookup always missed. Extended the mcpServers fix: `GroupSummary` now also tracks
  `serviceCollectionIds` (agent rows only) and emits it as `mcpServerCollectionIds`;
  `connectedMcps` attaches each server's own `collectionIds` directly onto the row so
  `AgentMcpToolsView` reads `selectedMcp.collectionIds` straight off the clicked row instead of
  searching the always-empty `agenticFlatData` — which was then removed as dead plumbing from
  `AgenticComponentsTab`/`AgentComponentsView`/`AgentMcpToolsView` (still used elsewhere for
  `OverviewTab`'s topology graph, untouched). Verified live: the API now returns real per-server
  collectionIds (e.g. `mcp.razorpay.com: [726319791, 312535445, 2070401825]`) and
  `AgentMcpToolsView` correctly calls `fetchMcpComponentsData` with those exact ids (confirmed via
  network tab) instead of never firing meaningfully. Still shows "No tools found." because this
  demo dataset has no captured MCP tool-call traffic anywhere — confirmed by checking a standalone
  MCP Server asset ("default") through the separate, untouched `McpComponentsView` path, which
  shows 0 components too. The fix corrects the wiring; the empty result is real data, not a bug.

## High priority — wrong output today

- [ ] **NHI Violations' default "sort by severity" is alphabetical, not by rank.**
  `NhiGovernanceViolationsAction.java:116-124` (`mapSortField`) maps `"severity"` straight to the
  raw string field and `Aggregates.sort()` does a lexicographic sort. Alphabetical-descending of
  `Critical/High/Medium/Low` is `Medium, Low, High, Critical` — the opposite of what the page's
  own default-desc-sort column implies.
  **Fix:** add a computed rank field via `$addFields`/`$switch` in the aggregation pipeline (map
  Critical→4 … Low→1) and sort on that instead of the raw string, or hardcode a
  `Sorts.orderBy` on a `$cond` expression. Match the frontend's existing `SEV_ORD` mapping.

- [ ] **Device username resolution does the opposite of what its own comment says.**
  `AgenticObserveAction.java:924-939`. Comment: *"Endpoint Shield username resolution takes
  priority over the device-module fallback."* Code: sets `gs.username` from device-module
  metadata at group-creation time; only calls `resolveUsername()` (Endpoint Shield) if still `"-"`.
  **Fix:** swap the order — try `resolveUsername()` first, fall back to `deviceMeta`'s username
  only if that's blank/`"-"`, matching the comment and `agenticPageBuilders.js`'s original
  `resolvedUsername !== DEFAULT_VALUE ? resolvedUsername : mod.username`.

- [ ] **A new pagination-reset behavior in the shared grid reaches an untouched, untested page.**
  `AgGridTable.jsx:271-279` — the new "jump to page 1 on search" effect fires for any
  `serverSideRowModel` + SSRM caller, including `guardrails/violations/ViolationsPage.jsx`, which
  has zero changed lines in commit `98da9ff302` and no coverage in this PR's e2e suite.
  **Fix:** not necessarily wrong (the behavior is arguably a UX improvement), but needs an
  explicit look — either add e2e coverage for Guardrails Violations, or gate the new effect
  behind an opt-in prop so it doesn't silently apply to callers this PR never reviewed.

## Medium priority — feature regressions / data correctness

- [ ] **NHI Violations search no longer matches identity or policy names.**
  `NhiGovernanceViolationsAction.java:186-190`. Old client search matched almost any field; new
  server search is `Filters.or(regex(AGENT_NAME), regex(VIOLATION_TYPE))` only.
  **Fix:** extend the `Filters.or(...)` to also regex-match identity name / policy name (may need
  a `$lookup`-then-match or a denormalized searchable field, since policy name isn't on the
  violation document directly — check `policyLookupAndProjectStages()`).

- [ ] **Three NHI aggregation endpoints skip contextSource scoping.**
  `NhiGovernanceViolationsAction.java:283-321` (`fetchViolationCountsByIdentity`,
  `fetchViolationCountsByPolicy`) and `:345-367` (`fetchViolationsByIdentity`'s row/severity
  pipelines) build raw `aggregate()` pipelines without calling `buildBaseMatchConditions()`, unlike
  `fetchAllViolations`/`fetchViolationsStats`. On multi-contextSource accounts this mixes counts
  across sources; in `fetchViolationsByIdentity` specifically, the scoped `total` (via DAO
  `.count()`) can disagree with the unscoped returned rows.
  **Fix:** add `Aggregates.match(combineMatch(buildBaseMatchConditions()))` (or equivalent) as the
  first stage in all three pipelines.

- [ ] **Agentic Assets lost Type/Tags column filters and violation-count sort, no replacement.**
  `AgenticAssetsPage.jsx` — old table supported one-click filter by Type, by Tags, and sort by
  total violations; new summary endpoint has no equivalent params, only free-text name search.
  **Fix:** either add `clientType`/tag/severity filter params to `fetchAgenticAssetsSummary` and
  wire real AG-Grid column filters back in, or explicitly document this as an accepted scope cut
  if the filters aren't coming back.

- [ ] **Bulk "Edit team & role" only sees the last-fetched page of rows.**
  `UsersAndDevices.jsx:86,187,237-238` — `lastRowsRef.current` is overwritten on every
  page/sort/search change; `openEditTagModal` filters it by `usernames.includes(r.id)`, so
  selections made on an earlier page silently vanish once the ref is overwritten by a later fetch.
  **Fix:** accumulate selected *row objects* (not just ids) in a ref/map keyed by id, updated
  incrementally as pages are fetched and as selection changes — not wholesale-replaced per fetch.

- [ ] **Endpoints page: Users delta differs between prod and localhost by a couple of users
  (+18 vs +20 observed on one account) — likely the already-known timezone-bucketing tradeoff
  below, not a new bug**, but not conclusively confirmed. If it recurs after that's fixed, treat as
  a separate issue.

## Low priority — edge cases

- [ ] **Shared `?asset=` deep links can silently fail to open.**
  `AgenticAssetsPage.jsx:212-228` — new auto-open effect only searches rows already loaded into
  the SSRM grid, no slug-normalization fallback, no retry as more rows load. Already acknowledged
  in-code as a known regressed corner case.
  **Fix:** on no-match, fall back to a direct lookup call (fetch by exact id/name) instead of only
  scanning currently-loaded grid rows, so the flyout can open regardless of the row's page/sort
  position.

- [ ] **`cacheBlockSize` is pinned to the initial `paginationPageSize` prop, not the grid's live
  page size.** `AgGridTable.jsx:365` — if any SSRM caller exposes a runtime page-size selector to
  users, cache blocks stop aligning with displayed pages once the size changes.
  **Fix:** derive `cacheBlockSize` from the grid API's current page size (e.g. via
  `onPaginationChanged`) instead of the static prop, or confirm no current caller exposes the
  selector and drop `paginationPageSizeSelector` where unused.

- [ ] **Trend-chart month bucketing moved from browser-local to server-local timezone.**
  `AgenticObserveAction.java` (`buildWindowSlots`/`monthStartEpoch`, `ZoneId.systemDefault()`).
  Already identified and accepted as a minor risk earlier in this branch's history — listed here
  only so it isn't lost. Can shift a trend bucket by one month for events near a month boundary
  if server and viewer timezones differ.

## Duplication & efficiency (non-blocking cleanup)

Lower urgency — none of these produce a wrong result today, but multiple independent review
angles flagged the same patterns, so they're worth batching into one cleanup pass:

- [ ] Pagination-clamp logic (`effectiveLimit`/`from`/`to`) hand-copied 6× across
  `AgenticObserveAction.java`, `NhiGovernanceViolationsAction.java`, `ModuleInfoAction.java`, with
  inconsistent default caps (50/500 vs 20/200). Extract one shared `paginate(list, skip, limit)`.
- [ ] `fetchAgenticAssetsSummary`/`fetchUsersAndDevicesSummary`/`fetchDeviceEndpointsSummary` load
  **every** collection via `findAll(Filters.empty())` then classify+slice in memory on every
  request — not real DB-level pagination. Two sibling endpoints in the same PR
  (`NhiGovernanceViolationsAction.fetchAllViolations`, `ModuleInfoAction.fetchEndpointShieldAgents`)
  already do real `$skip`/`$limit` aggregation; worth matching that pattern here too.
- [ ] Client-side full-account maps (`trafficMap`/`riskScoreMap`/etc.) are re-POSTed in full on
  every grid interaction — moves the "multi-MB payload" problem this PR fixed from response to
  request. Consider computing these server-side (join/lookup) instead of round-tripping them.
- [ ] Two hand-maintained copies of the MCP client registry (`McpClientRegistry.java` vs
  `mcpClientHelper.js`), admitted in its own comment as having no shared source of truth. Also
  consumed by 9 other files outside this PR's scope — drift risk is wider than just these 3 pages.
- [ ] Sort-order sign inversion (AG-Grid/GithubServerTable → Mongo convention) reimplemented 4×
  with two different (equivalent) formulas, instead of matching `IssuesPage.jsx`'s existing
  pass-through convention.
- [ ] Four independent `SORT_FIELD_MAP` + `onServerFetch` callbacks with inconsistent fallback
  behavior on an unmapped sort key (pass-through in 2 pages, silent-default-to-`riskScore` in the
  other 2).
- [ ] Duplicated tag-scan block (byte-for-byte) between `GroupSummary.accumulateCheap` and
  `HostGroupSummary.accumulate` in `AgenticObserveAction.java`.
- [ ] `buildAgenticAssetsPageData`/`buildGroupAggregates` in `constants.js` have zero importers
  post-rewrite but gained new caching/batching complexity in this commit — candidate for deletion.
- [ ] The "skip loadStats on the meaningless first refreshKey bump" bootstrap pattern is
  duplicated near-verbatim across all 3 rebuilt pages — candidate for a shared hook.
- [ ] `fetchUsersAndDevicesStats` classifies the same collection list twice (once per grouping) —
  could accumulate both in one pass.
- [ ] `fetchEndpointShieldFilterOptions` issues 4 sequential, independent `distinctStrings()`
  calls — candidate for one `$facet` or concurrent dispatch.
- [ ] `cumulativeCounts` called 11× independently instead of looping over a category map.
- [ ] `NhiGovernanceViolationsAction.fetchAllViolations` issues a separate `count()` round trip
  before its paginated aggregation — could use `$facet` (already used elsewhere in the same file).

## Reference

Full findings detail, severity rationale, and live cross-environment verification data:
the PR #6002 review dossier artifact (see PR description / conversation history for the link).
