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
- NHI Governance Identities page (`IdentitiesPage.jsx`) had **zero** server-side pagination —
  `fetchNhiIdentities()` pulled the whole account's identity list via `findAll` and paginated
  client-side via `GithubSimpleTable`. Added `NhiGovernanceIdentitiesAction.fetchAllNhiIdentities()`
  (real Mongo `$skip`/`$limit`/`count()`, mirroring `fetchAllViolations`'s pattern) and wired the
  table to it. First pass used `AgGridTable` by analogy with the sibling `ViolationsPage.jsx` — user
  caught that this was the wrong convention ("got changed into new layout, that was in old layout
  ... refer the master version") and that it was slower than before. `ViolationsPage.jsx`'s AG-Grid
  choice was specific to that page (commit `937bfa384a`), not a general NHI Governance pattern; the
  actual established convention for `IdentitiesPage.jsx` (per that same commit, matching
  `UsersAndDevices.jsx`) is `GithubServerTable`'s real server-mode. Reverted to that, and reverted
  the Identity flyout's Violations tab (`IdentityDetailsPanel.jsx`) entirely — its
  `IDENTITY_VIOLATIONS_LIMIT = 200` bounded single-fetch design was a deliberate existing choice,
  not a bug, and didn't need touching. `ViolationsPage.jsx` itself needed no changes — confirmed
  already correctly paginated. **Lesson**: match the established convention for the specific page
  being changed, not whatever a sibling page happens to use.
- `fetchAllNhiIdentities()`'s match-condition builder copied a `Context.contextSource` filter from
  `NhiGovernanceViolationsAction.buildBaseMatchConditions` defensively, without checking whether it
  was actually needed. It wasn't: `NhiIdentityDao extends AccountsContextDao` (plain — never
  auto-scopes by contextSource for any query), unlike `NhiViolationDao extends
  AccountsContextDaoWithContextSource` (which auto-injects that filter, which is why the violations
  action needs to manually replicate it only for its raw-aggregate bypass). The spurious filter
  silently excluded identities whose `contextSource` didn't match the current request's context,
  causing the "Expired" tab to show 38 rows against its own "50" count badge on a real account
  (Acorns Demo) — reproduced live, matching the user's report, and confirmed fixed (`total: 50`,
  matching the tab badge, after removing the filter).
- Even after the pagination fix above, `IdentitiesPage.jsx`'s topology graph and summary
  cards/tab counts still fed off `fetchNhiIdentities()` — unpaginated, whole-account — because
  `IdentityOverviewGraph` needs "every identity" to fan out each agent correctly. On the Atlas
  Scale Test account (13,817 identities) this meant a 13.8k-row fetch on every load, and
  `IdentityOverviewGraph` rendering one ReactFlow node per identity with no virtualization —
  the graph tab became completely unresponsive (no click, no zoom/pan). No existing page in this
  PR had already solved "feed a graph/summary without pulling every row" (checked
  `DeviceEndpoints.jsx`'s topology tab and the `fetchAgenticAssetsSummary`-style endpoints —
  none of them do real server-side aggregation; they load-all-then-classify-in-memory, which is
  itself listed as tech debt below). Built two new lightweight calls instead: a
  `fetchNhiIdentitiesStats` action doing four independent `count()` queries (total/expired/
  disabled/with-violations, the last via `Filters.in(IDENTITY_NAME, ...)` against the identity
  names already known to have violations from the existing cheap
  `fetchViolationCountsByIdentity`) for the summary cards and tab badges, and reused the already-
  paginated `fetchAllNhiIdentities` with a 200-row cap (`createdAt` desc) to feed the graph,
  matching the identity flyout's existing `IDENTITY_VIOLATIONS_LIMIT = 200` precedent. Added a
  "Showing the 200 most recently discovered identities of 13,817 total" notice to the graph when
  capped. `fetchNhiIdentities()` itself is untouched — `CreateNhiPolicyModal.jsx` still needs the
  full list for its dropdown options and wasn't part of this bug report. Verified live on Atlas
  Scale Test: graph renders ~200 nodes instead of 13,817, clicking a node opens the identity
  flyout correctly, summary cards/tab badges show the true account-wide counts (13,817 total /
  6,325 expired / 1,953 disabled / 6,259 with violations) while the graph fetch payload dropped
  from the full account to exactly 200 documents.
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
- Agentic Assets flyout's three data-heavy tabs (Components, Violations, Devices) fetched their
  *entire* dataset once per open and paginated client-side in AG-Grid, instead of the true
  server-side skip/limit the top-level 777-row grid already had. Not a regression from this
  rebuild (master's client-side pipeline did the same), but flagged directly: an agent with
  hundreds of skills/violations, or a large device fleet, would load everything on every flyout
  open. Also flagged: `AgentComponentsView.jsx`'s `fetchSkillsFlyoutData` fired a full-account
  `getAllCollectionsBasic()` scan (unfiltered, unpaginated dump of every `ApiCollection`) once per
  collection id in the asset — for an agent with N collections, N redundant full-account scans
  just to look up one collection by id each time, on top of `3N` other per-collection round trips.
  All three tabs converted to genuine server-side pagination:
  - **Devices**: new `AgenticObserveAction.fetchAgenticAssetDevicesPage`, scoped to just the
    asset's own `apiCollectionIds` (cheap — one `Filters.in()` query, not the account-wide
    `classifyAllGroups` walk), resolving `username` via the same Endpoint Shield `usernameMap` so
    search/sort work against the person's name.
  - **Components**: new `AgenticObserveAction.fetchAgenticComponentsPage` — a Java port of
    `buildSkillsFlyoutData`/`buildMcpComponentsFromStis`/`buildAgentBuiltinToolsFromStis`, batched
    across the asset's whole `collectionIds` (3 total queries instead of `3N+N`). MCP-server rows
    fold in from the already-known `mcpServers`/`mcpServerCollectionIds` (no re-derivation); the
    synthetic "Config" row stays client-side via `pinnedTopRowData` since it's derived from
    violations data this endpoint has no reason to touch.
  - **Violations**: `SuspectSampleDataAction`/threat-detection-backend's
    `MaliciousEventService.listMaliciousRequests` already had real skip/limit/hosts-filter/sort —
    just never exercised for pagination (`fetchAgenticViolations` always pulled `limit=100000`).
    Wired the existing capability through, and extended threat-detection-backend's
    `ListMaliciousRequestsRequest.Filter` proto with `search_text`/`loose_host_keys`/
    `claude_device_ids`/`match_claude_config` so the exact three-tier host attribution
    `ViolationsTab.jsx` always did client-side (exact host / loose device+service key / claude-config
    scanner events) now happens server-side too, keeping pagination boundaries correct. Kept a
    separate `fetchAgenticViolationsPage` function rather than changing `fetchAgenticViolations`'s
    return shape, since `DeviceFlyout.jsx` still calls the latter expecting a plain array.
  All three verified live end-to-end against Cursor (73 violations / 28 components / 4 devices):
  correct page counts, working search (including a non-matching term correctly returning zero,
  and a host-only match on "rakshak" still returning all 73), and no new console errors. The
  Violations tab's threat-detection-backend change required a `--full` rebuild
  (`./run-tbs.sh --full`) to pick up the new proto fields — a quick `mvn package` alone reuses
  already-installed `.m2` artifacts and silently keeps the old proto, causing every request to
  fail proto parsing with a 400 until the full rebuild ran.
- Endpoints page's "Total Violations" stat card was missing its "+N" delta badge, even though the
  raw API response and the consuming `AgenticStatsCard` were both already correctly wired —
  `fetchDeviceEndpointsStats` in `api.js` whitelisted `deviceCount`/`browserDeviceCount`/
  `totalUsers`/etc. off the raw response but omitted `deltaViolations`, silently dropping it before
  it ever reached the component. Fixed by adding the missing field to the wrapper's return object.
  Verified live: card now shows "107 +107" matching the raw network response.
- Endpoints page's device flyout ("Agentic Assets" and "Violations" tabs) was on the same
  fetch-everything-then-paginate-client-side pattern the main Agentic Assets flyout tabs were
  fixed from earlier in this branch — converted both to true server-side pagination, matching that
  same pattern. "Agentic Assets" reuses `fetchDeviceEndpointsSummary`'s existing `parentDeviceId`
  branch (`AgenticObserveAction.java`), which previously returned every child row unpaginated;
  now applies search/sort/skip/limit there. "Violations" reuses `fetchAgenticViolationsPage`
  (already built for the asset flyout), scoped to the device's own hostNames/deviceId instead of
  an asset's. Also dropped the `agentRiskData` side-map in `DeviceFlyout.jsx`/`DeviceEndpoints.jsx`
  — it only re-derived `riskScore`/`violations` already present on each row, so the grid now reads
  them directly (matches `AgentComponentsView.jsx`'s existing pattern).
  **Regression caught and fixed during live verification**: making `fetchDeviceEndpointsSummary`'s
  `parentDeviceId` branch unconditionally paginate broke `DeviceEndpoints.jsx`'s own
  `fetchDeviceChildren` helper (used for the Overview tab + tab-count label), which had never
  needed to pass `skip`/`limit` since the old code path ignored them — after the change it was
  silently truncated to the new endpoint's fallback default of 20 rows, producing a visible
  "Agentic Assets (20)" tab label against a grid correctly showing "1 to 20 of 48". Root cause: a
  Java `int limit` field defaults to `0` when the caller's JSON omits it, and `effectiveLimit =
  limit > 0 ? ... : 20` can't distinguish "caller wants everything" from "caller wants the default
  page size." Fixed by having `fetchDeviceChildren` explicitly pass `limit: 500`. Verified live by
  closing/reopening the flyout twice to rule out a background-cron timing artifact before
  concluding it was a genuine regression, then confirming the tab label and grid total both read
  48 after the fix. Also verified both tabs' search boxes (a non-matching term correctly returns
  "0 to 0 of 0", a real term correctly narrows results) and no new console errors.
- Endpoints page's main grid Filters side panel rendered broken: "Endpoint"/"Group"/"Role"/
  "Last Traffic" showed a Set Filter (checkbox list + "(Select All)") with no actual values
  underneath, and "OS" was missing entirely. Root cause: under AG Grid's server-side row model,
  `filter: true` always resolves to a Set Filter, but SSRM can't auto-derive checkbox values from
  a partial/paginated dataset the way client-side row model does — nothing was supplying
  `filterParams.values`. `os` had in fact been a real, working `agSetColumnFilter` on
  origin/master's pre-SSRM-rewrite client-side version (client-side mode derives values from the
  full loaded dataset for free) but got disabled (`filter: false`) somewhere in the rewrite with no
  replacement. Fixed by populating Set Filter values for `os`/`group`/`role` from
  `deviceMetadataMap` — already fully loaded client-side at mount, no new network call — and wiring
  the selected values through `onServerFetch` into `fetchDeviceEndpointsSummary`, mirroring the
  filter-application pattern the sibling `fetchUsersAndDevicesSummary` method already had.
  `deviceId`/`lastTraffic` (non-enumerable: near-unique per row / a timestamp) got `filter: false`
  instead — `deviceId` already has the grid's own search box. Caught and fixed a second bug during
  verification: unchecking the *only* available value sent `filters.os: []`, which a naive
  null-or-empty check would treat as "filter not applied" (showing everything) instead of "match
  zero rows" (real Set Filter semantics) — fixed by checking `containsKey` instead. Verified live:
  OS filter now shows real values ("mac"), unchecking it narrows the grid to zero rows, and
  re-checking restores all 5.
- New layout's "Endpoint" column and old layout's Devices tab had no way to filter down to one
  device or its owner (only os/group/role existed on the new layout; the Devices tab had zero
  filters at all on the old layout — Team/User role only apply on the Users tab). Added both,
  sourcing values from lists the account was already computing for other reasons — no extra query.
  `fetchDeviceEndpointsStats` already derives every device's `extractEndpointId(hostName)` grouping
  key for the trend charts (`deviceFirstSeen.keySet()`); exposed as `deviceIds` and used verbatim
  (not re-derived client-side) so the filter's values are guaranteed to match the grid's own
  grouping key exactly. `fetchUsersAndDevicesStats` already resolves each device's owner username
  (Endpoint Shield first, device-module fallback) via `classifyHostGroupedRows`'s device grouping;
  exposed as `usernames`. Verified live: new layout's Endpoint filter lists all 5 real device ids
  and narrows the grid when one is excluded; old layout's Devices tab now shows a "User" filter
  pill listing all 4 real usernames, and selecting "jane.doe" narrows "Showing 1-5 of 5" to
  "Showing 1-1 of 1" (her one device).
- Old layout's Users/Devices tab switcher was invisible on a fresh page load — found as a side
  effect while adding the Devices tab's "User" filter above, not something this branch introduced.
  `UsersAndDevices.jsx` passed `mode={IndexFiltersMode.Filtering}` to `GithubServerTable`, the only
  caller among 15+ pages using this shared component to do so (every other page passes
  `IndexFiltersMode.Default`). Polaris's `IndexFilters` renders a completely different UI in
  Filtering mode (a "Cancel" + sort-icon row, mid-edit-looking) instead of its normal tab-bar +
  search + filter-icon layout, so the `tabs={props.tableTabs}` prop (correctly wired to
  `["Users (N)", "Devices (N)"]`) never rendered until a user happened to click "Cancel" first,
  which switched to Default mode and revealed the tabs. Fixed by passing
  `IndexFiltersMode.Default`, matching every other caller. Verified live: fresh load of
  `/dashboard/observe/users-and-devices` now immediately shows "Users (4) / Devices (5)" tabs and
  the normal search+filter-icon layout, and switching between tabs still works correctly.
- Agentic Assets' new layout page had every column's filter disabled (`filter: false`), including
  "Type" and a "Tags" column that had been dropped entirely — both existed as working Set Filters
  on origin/master's pre-SSRM-rewrite client-side version (client-side row model auto-derives Set
  Filter values from the full loaded dataset for free; SSRM can't, and nothing filled the gap).
  Restored both with hardcoded value lists, since each is a small fixed enum rather than something
  to derive from data: `["AI Agent", "MCP Server", "LLM", "Skill"]` for Type
  (`AgenticObserveUtil.CLIENT_TYPE_*`), `["Contains personal account", "Local MCP Server",
  "Malicious Skill", "Misconfigured"]` for Tags (matching `shapeRow`'s own tag derivation exactly)
  — same pattern `guardrails/violations/ViolationsPage.jsx` already uses for its severity filter.
  Tags is an array-valued field (Set Filter semantics: a row matches if ANY selected tag is
  present); its "Malicious Skill" branch needed `maliciousSkillKeys` threaded from the client
  (account-wide `<collectionId>|<skillName>` set, already fetched once there, otherwise unknown to
  the Java action). The old `agTextColumnFilter` on the "Agentic Assets" name column was
  deliberately not restored — the grid's own search box already does the same substring match
  server-side, so it wouldn't add real capability, just a duplicate entry point. Verified live:
  Type filter unchecking "Skill" narrows 777 → 44 (exactly Agents + MCP Servers, no Skills);
  unchecking "Contains personal account" from Tags (leaving Local MCP Server/Malicious
  Skill/Misconfigured) narrows 777 → 20, and all 20 visible rows correctly show the "Local MCP
  Server" badge.
- NHI Governance's Identities page had **zero** server-side pagination —
  `NhiGovernanceIdentitiesAction.fetchNhiIdentities()` did an unbounded `findAll(filter)` with no
  skip/limit/count at all, pulling every identity in the account into the browser on every load,
  then paginating client-side via `GithubSimpleTable`. **First attempt at this fix incorrectly
  converted the table to AG Grid** (matching the sibling `ViolationsPage.jsx`'s SSRM setup) — wrong
  call: NHI Governance's own established convention (see `937bfa384a` "Rebuild ATLAS agentic pages
  onto server-side pagination", which explicitly did this for `UsersAndDevices.jsx`) is
  `GithubServerTable`'s own real server-mode, "bypassing the client-side-only `GithubSimpleTable`
  adapter... so the original Polaris-native table UI is preserved exactly while the data underneath
  is paginated" — not a framework swap to AG Grid. AG Grid also measurably added overhead (a second
  full unpaginated fetch was still needed for `IdentityOverviewGraph`'s topology graph on top of the
  new paginated table fetch, plus AG Grid's own heavier client-side footprint), which showed up as a
  real slowdown against the scale-test account. Corrected to swap `GithubSimpleTable` for
  `GithubServerTable` directly, keeping every other prop (headers, tabs, bulk actions, row click)
  identical to before — only the data-fetching prop changes (`data={array}` → `fetchData={callback}`)
  — mirroring `UsersAndDevices.jsx`'s `fetchData(sortKey, sortOrder, skip, limit, filtersObj,
  filterOperators, queryValue)` signature exactly. Backend: added `fetchAllNhiIdentities` (real Mongo
  `$skip`/`$limit`/`count()`, mirroring `fetchAllViolations`'s own pattern) while keeping
  `fetchNhiIdentities()` unpaginated — it now serves only the topology graph, which genuinely needs
  every identity to fan out each agent's full list correctly (same "separate full-fetch for the
  graph, paginated fetch for the table" split `DeviceEndpoints.jsx`'s Overview tab already
  establishes). Default sort changes from the old client-side "most violations first" ranking to
  `createdAt` descending (most recently discovered first) — replicating the old ranking server-side
  would need a `$lookup` into `nhi_violations` since identities don't carry a denormalized violation
  count; flagging this as a deliberate, visible behavior change. The identity flyout's own
  "Violations" tab (`IdentityDetailsPanel.jsx`) — initially also (incorrectly) converted to AG Grid —
  was reverted back to its original, deliberate design: a single bounded `limit: 200`
  `GithubSimpleTable` fetch, per the explicit comment already in that code ("A single identity's
  violations are bounded by usage against that one credential... one page comfortably covers
  virtually every identity") — not a bug, a documented tradeoff, left untouched. Verified live
  against the demo account: all 12 identities render correctly with no AG Grid overhead and zero
  console errors (previously 7+ from the AG Grid Enterprise license banner alone), tab switching
  ("All"/"Expired"/"Disabled") re-fetches from the server with matching counts and a proper "No
  identities found" empty state, search narrows 12 → 4 for "notion", and row click still opens the
  (reverted, unchanged) identity flyout correctly. The main Violations page itself
  (`NhiGovernanceViolationsAction.fetchAllViolations` + `ViolationsPage.jsx`'s AG Grid `onServerFetch`)
  was independently confirmed via code review to already be a fully correct, working SSRM
  implementation end-to-end — its own AG Grid usage predates this round of fixes (part of
  `937bfa384a`) and was left as-is; no changes needed or made there.

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
