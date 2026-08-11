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
  disabled/with-violations, the last via `Filters.in(_id, ...)` against the identity ids already
  known to have violations from the existing cheap `fetchViolationCountsByIdentity` — originally
  written against `identityName` instead of `_id`, which turned out to be the same non-unique-name
  bug fixed two bullets below, and was corrected alongside it) for the summary cards and tab
  badges, and reused the already-
  paginated `fetchAllNhiIdentities` with a 200-row cap (`createdAt` desc) to feed the graph,
  matching the identity flyout's existing `IDENTITY_VIOLATIONS_LIMIT = 200` precedent. Added a
  "Showing the 200 most recently discovered identities of 13,817 total" notice to the graph when
  capped. `fetchNhiIdentities()` itself is untouched — `CreateNhiPolicyModal.jsx` still needs the
  full list for its dropdown options and wasn't part of this bug report. Verified live on Atlas
  Scale Test: graph renders ~200 nodes instead of 13,817, clicking a node opens the identity
  flyout correctly, summary cards/tab badges show the true account-wide counts (13,817 total /
  6,325 expired / 1,953 disabled / 6,259 with violations) while the graph fetch payload dropped
  from the full account to exactly 200 documents.
- **Identities table's violation badges showed inflated counts that didn't match the identity's
  own flyout** (e.g. a row showing "85/82/135" critical/high/medium while its flyout's Violations
  tab showed 0-5). Root cause: `fetchViolationCountsByIdentity` (`NhiGovernanceViolationsAction.
  java`) grouped violations by `identities.identityName` — a display label, not a unique key. On
  the Atlas Scale Test account, `identityName` is wildly non-unique (319 distinct identities
  across different agents/owners all share the literal name "razorpay-remote-akto.Authorization"),
  so every row's badge was really the sum of violations across every identity sharing that name,
  not just that one row's own count. `fetchViolationsByIdentity` (the flyout's own fetch) was
  already correct — it scopes by `identities.id`, the actual per-identity ObjectId. Fixed by
  grouping the aggregation by `identities.id` (`$toString`'d to a hex string) instead of name,
  and propagating the rename (`identityNames` → `identityId`/`violatingIdentityIds`) through
  `IdentitiesPage.jsx`'s `violationIndex` and the `fetchNhiIdentitiesStats` "Identities with
  Violations" count (same bug, different call site — see the correction noted in that bullet
  above). Verified live: a "razorpay-remote-akto.Authorization" row's badge ("2") now matches its
  flyout's Violations tab exactly ("Showing 1-2 of 2").
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
- **Opening the Agentic Assets flyout for an "AI Agent"/"MCP Server"/"LLM" row fired 3 network
  requests PER collection id in that row's group** (`fetchApisFromStis`, `fetchApiInfosForCollection`,
  `fetchMcpAuditInfoByCollection` via `agenticObserveApi.fetchCollectionStiBundle`,
  `AgenticAssetFlyout.jsx`'s two top-level effects, plus the same pattern in
  `McpComponentsView.jsx`/`AgentComponentsView.jsx`'s Components tab). A summary row is a *group*,
  not one collection — on Atlas Scale Test, agent groups alone span 25,272 of 25,890 collections —
  so opening one flyout could fire tens of thousands of concurrent requests and choke the browser
  tab (user-reported: "clicking on entry ... causing too many api calls ... browser got choked").
  Fixed by adding optional batch variants to the three backend endpoints
  (`InventoryAction.fetchApiInfosFromSTIs`/`fetchApiInfosForCollection`,
  `AuditDataAction.fetchMcpAuditInfoByCollection` — each now accepts `apiCollectionIds` alongside
  the existing single `apiCollectionId`, fully backward compatible) and a new
  `fetchCollectionStiBundlesBatch` on the frontend: 3 requests total per flyout open regardless of
  group size, not 3 per collection. `fetchApiInfosForCollection`/`fetchMcpAuditInfoByCollection`
  batch via true single/two-query `$in` lookups; `fetchApiInfosFromSTIs` loops server-side (its
  host-based STI pagination has no clean single-query multi-collection form) — same total DB work,
  but one round trip from the browser instead of N, which is what was actually choking the tab
  (connection/promise explosion client-side, not per-query cost). All three cap at 300 ids.
  Verified by calling the old and new endpoints directly (authenticated fetch) against 6 real
  collection ids: identical result counts for every id.
- ~~The Agentic Assets new-layout grid renders 0 rows on every account tested~~ — **correction, not a
  real bug**: this was logged earlier in this doc as a suspected pre-existing/RBAC issue, but it was
  actually a testing mistake on my part. `ApiCollectionsDao extends AccountsContextDaoWithRbac`,
  which scopes every query by the request's `x-context-source` header (derived from the top-left
  product-category switcher — "API Security" -> `API`, "Akto ATLAS" -> `AGENTIC`). I'd been testing
  the Agentic Assets page while the category switcher was still on "API Security" (its default),
  so `getAllCollectionsBasic`/`fetchAgenticAssetsSummary` correctly scoped to that account's ~49
  *API*-context collections — none of which are agentic (no browser-llm/asset tags), hence 0
  classified groups. Switching the category to "Akto ATLAS" before testing immediately showed real
  data (795/777 assets depending on account, real violations, working flyout). No code fix needed;
  flagging so nobody re-diagnoses this as a backend bug — **always set the product category to
  "Akto ATLAS" before testing any agentic page.**
- `fetchAgenticAssetsSummary`'s `onServerFetch` re-POSTs `trafficMap`/`riskScoreMap`/`sensitiveMap`/
  `usernameMap`/`userMetadataMap` in full on **every** grid page turn — one observed request body
  was 722KB for a 49-collection account. Already listed below under "Duplication & efficiency" as
  a known pattern; this is a concrete size data point for that item, not a new finding.
- **`fetchAgenticAssetsSummary`'s response measured at 16MB for a single 50-row page** (Atlas Scale
  Test). User pushed back on treating this as "just cache/paginate it" and asked what the grid
  actually needs from the response. Traced every field in `GroupSummary.toSummaryResponse()` plus
  the per-row `devices` list `fetchAgenticAssetsSummary` separately attached, and found the main
  grid only ever reads small scalars — the bulk of the payload was per-group member lists
  (`hostNames`/`collectionIds`/`skillNames`/`mcpServers`, up to ~25k entries for one giant group,
  used only by the flyout for the ONE asset a user opens) and the raw per-device breakdown (up to
  hundreds of entries per row, used only to derive a small Teams breakdown + an AI-interactions
  total). Removed all of it from the paginated response; added `violations`/`isMalicious`/`groups`/
  `aiInteractions` precomputed server-side instead (the server already has this data in memory
  during classification — no new queries), and a new `fetchAgenticAssetDetail` lazy per-asset
  endpoint (`groupKey`+`rowType` -> the removed fields, plus `devices`) that the flyout calls once
  when a user actually opens an asset, behind a brief loading spinner — unchanged from every tab
  component's perspective, just populated one render later. Also discovered mid-fix: the "0 rows"
  item above, which blocked verification until resolved. Verified live: a 50-row page returns
  compactly (no longer needs saving to a file, unlike the prior 16MB/8.6MB intermediate responses);
  a "Cursor" row's precomputed `violations:{medium:73}` matches its flyout's Violations tab exactly.
- **`getAllCollectionsBasic` (mount-time full-account fetch, `ApiCollectionsAction.fetchAllCollectionsBasic`
  — `Filters.empty()`, no skip/limit, ~19 fields/doc, several MB on large accounts) eliminated
  entirely from the Agentic Assets page**, not just cached or slimmed. User pushed back on
  "generalize the existing cache" (the cache is hardcoded to one specific account id, so it never
  engaged elsewhere) by asking why a *grouped* display needs *per-collection* data at all — traced
  every real usage and found only two of the ~19 fields were ever read (`id`, `hostName`), both
  purely to join server-aggregated per-host violation counts back to collection ids client-side
  (`buildHostAttributionMaps`/`resolveHostToCollectionIds`, a 3-tier exact/loose/claude-config
  match) and, in the flyout's Violations tab, to look up one asset's own hostnames — both of which
  the server already has everything needed to do itself. Fixed by porting the matching logic to
  Java (`AgenticObserveAction.attributeViolationCountsToCollections`, loads only `{id, hostName}`
  to do the join, returns counts pre-keyed by collection id) and by having `ViolationsTab.jsx` read
  `hostNames` straight off the asset row (`GroupSummary` already collects every member collection's
  hostName server-side, per row, via `toSummaryResponse()` — this data was already being shipped to
  the browser and simply wasn't being used). `fetchAndCacheAgenticCollectionsBundle` — shared by 4
  other agentic pages (`UsersAndDevices`/`Endpoints`/`DeviceEndpoints`/`EndpointPosture`), not
  audited in this pass — was left untouched; added a smaller sibling
  (`fetchAndCacheAgenticCollectionsBundle` → `fetchAndCacheAgenticTrafficRiskBundle`, own
  `PersistStore` cache slot) fetching only `trafficMap`/`riskScoreMap` for this one page instead.
  **Those 4 other pages likely have the same eliminable dependency** (same shared bundle, same
  join pattern is plausible) — worth auditing as a follow-up rather than assuming they're fine.
  Verified live on Atlas Scale Test (25,890 collections): a clean page load fires zero
  `getAllCollectionsBasic` calls, while the new attribution call returns correct real counts
  (e.g. `{"1738051842": {"critical":43,"high":896,"medium":120,"low":0}}`).
- **Legacy Agentic Assets page (`Endpoints.jsx`, `/agentic-assets-legacy`) had the same anti-pattern
  as the new layout before this branch's earlier fixes**: one mount-time
  `fetchAndCacheAgenticCollectionsBundle` (full `getAllCollectionsBasic`) into a client-paginated
  `GithubSimpleTable`. User explicitly asked for this page to be fixed too. Rewrote it to
  `GithubServerTable` with real `skip`/`limit` against `fetchAgenticAssetsSummary` (same endpoint
  the new layout uses), the leaner `fetchAndCacheAgenticTrafficRiskBundle` bootstrap instead of the
  full collections bundle, async skill-risk enrichment patched in via a `refreshKey` bump (replacing
  the old page's bespoke `applySkillRiskScores`/`skillEnrichVersion` client-patch mechanism, no
  longer needed since `isMalicious`/risk scores are now server-computed), and a row-click handler
  that lazily calls `fetchAgenticAssetDetail` for the one clicked asset before navigating to
  Inventory (kept navigate-to-Inventory behavior, no flyout added — user's explicit choice).
  Restored `tagKey`/`rawTagValues` on `GroupSummary` (removed in the response-shrink fix above as
  apparently dead) since this page's Inventory-filter building needs them for agent rows — exposed
  via the existing lazy `fetchAgenticAssetDetail` endpoint, not the eager summary (cheap/bounded,
  doesn't reopen the 16MB problem). Added a `sensitiveTypes` accumulator (server-side, from the
  already-fetched sensitive-data map) and a `totalEndpoints` stat so the page's "Sensitive data"
  column and endpoint-count card have real values instead of being dropped.
  Two bugs found live-testing this before it shipped, both fixed: (1) `GithubServerTable` takes its
  filter-chip choices from a separate `filters` prop, not from `headers`' `filterKey`/`showFilter` —
  the Tag (Malicious/Misconfigured) facet was rendering as a chip-less no-op; added a `filtersDef`
  memo (`{key: "assetTags", label: "Tag", choices: [...]}`) matching `UsersAndDevices.jsx`'s own
  precedent. (2) `GithubServerTable.handleSort` expects `sortOptions[].columnIndex` to be the
  heading's 0-based array position **+ 1** (matches this same file's shared default `sortOptions`
  export, columnIndex 2/4/5/7 for Name/Endpoints/RiskScore/LastTraffic) — the new page-local
  `sortOptions` used the raw position (1/3/4/6), so clicking any sortable column header threw
  `Cannot read properties of undefined (reading 'value')`. Verified live on "My account": table
  numbers match the new layout exactly for the same account (777/AI Agents 24/MCP Servers 20/
  LLMs 0/Skills 733), zero `getAllCollectionsBasic` calls, Tag filter returns exactly the one
  Misconfigured row ("Claude CLI", "Showing 1-1 of 1"), Name-column sort reorders correctly with no
  console errors, and row-click on both an agent row ("Cursor" → `envType__mcp-client=cursor`) and
  a service row ("default" MCP Server → hostName-list filter) navigates to Inventory with the
  correct filter.
- **`getAllCollections` (83MB, not the already-fixed `getAllCollectionsBasic`) fired on the legacy
  Agentic Assets page**, found via the user's own DevTools performance trace right after the fix
  above shipped. Traced the network event's initiator stack in the trace JSON straight to
  `Dashboard.jsx`'s `fetchAllCollections` — the root layout's mount-once bootstrap effect (deps
  `[]`, so it only ever runs once per full page load) that populates
  `allCollections`/`collectionsMap`/`hostNameMap`/`tagCollectionsMap` for the whole app, already
  carved out an exclusion for `/dashboard/observe/inventory` but not this route. This call is
  pre-existing and unrelated to any Endpoints.jsx change in this round — it fires on a cold/direct
  load of **any** non-Inventory page, before that page's own effects can resolve (Dashboard's
  effect body runs synchronously, well before any child's `await`ed fetch settles, so nothing the
  page itself does can win that race) — it simply became the next-most-visible bottleneck in the
  trace once the 16MB-per-page problem was fixed. Confirmed via grep that nothing under
  `pages/observe/agentic/` reads any of those four PersistStore fields, so extended the existing
  Inventory exclusion to `/dashboard/observe/agentic-assets-legacy` too. Verified live: a fresh
  full-navigation load of the legacy page fires zero `getAllCollections`/`getAllCollectionsBasic`
  requests, table/stats still render correctly (777/24/20/0/733), no new console errors.
  **The same likely applies to the other 3 agentic pages sharing this pattern**
  (`agentic-assets`/`users-and-devices`/`endpoints`, i.e. `AgenticAssetsPage`/`UsersAndDevices`/
  `DeviceEndpoints`) since they equally don't read these fields, but wasn't extended there since
  only the legacy page was in scope for this round — worth a quick follow-up.
- **`fetchAgenticAssetsSummary` took 13-17s (up to 30s observed) for a ~5KB response on Atlas Scale
  Test (25,890 collections)**, reported right after the two fixes above shipped. Added timing
  instrumentation and found the culprit wasn't the Mongo query (`findAll` ~1-3s) but
  `classifyAllGroups` — the in-memory tag/skill/type classification pass over EVERY collection in
  the account — alone taking 30s+ under concurrent load. Root cause: `fetchAgenticAssetsSummary`,
  `fetchAgenticAssetsStats`, and `fetchAgenticAssetDetail` each independently re-ran this same O(N)
  pass on **every single request** — every sort click, filter change, tab switch, page turn, or
  asset open — with zero sharing between them, and a fresh page load fires several of these calls
  within seconds of each other (initial mount, stats card, async skill-enrichment refetch), so they
  piled up and contended for CPU simultaneously, multiplying the already-expensive per-call cost.
  Fixed by adding a per-account cache of the `(collections, groups)` classification result, keyed
  by `accountId`, TTL 60s (matching the frontend's own traffic/risk/sensitive bundle caching
  convention) — shared by all three endpoints. `ConcurrentHashMap.compute()`'s per-key locking
  turns concurrent cache misses for the same account into one shared rebuild instead of N redundant
  ones (the actual "thundering herd" that made the live numbers so much worse than a single
  isolated call). `fetchAgenticAssetDetail` was changed to forward its own real
  `trafficMap`/`riskScoreMap` into the cache lookup instead of empty maps — it never read those
  fields itself, but since the cache is now shared, a rebuild it triggered with empty maps would
  have poisoned the other two callers' traffic/risk data for the whole TTL window.
  `fetchAgenticAssetsStats` has the mirror-image gap for `sensitiveMap` (it never sends/reads that
  field either) — left as an accepted, self-healing, TTL-bounded edge case (at most 60s of blank
  "Sensitive data" icons if its call happens to win a rebuild race) rather than plumbing an unused
  field through a stats card that doesn't need it; flagged as a possible follow-up if it proves
  visible in practice. Also added a cheap sweep (piggybacked on the already-slow rebuild path, no
  extra thread) to stop the cache from growing unboundedly across many distinct accounts over a
  long server uptime. Verified live: cold cache build ~7-9s (was 13-30s, and now shared across
  concurrent requests instead of each paying it separately), warm-cache hits down to 20-400ms
  depending on tab/row count; numbers and filters/sort still match exactly (795/12/25/0/758).
  **`fetchUsersAndDevicesSummary`/`fetchDeviceEndpointsSummary` have the identical uncached
  findAll+classify pattern and would hit the same problem at this account's scale** — not fixed
  here (out of scope for this round), tracked under "Duplication & efficiency" below.
- **`fetchAgenticAssetsStats` still took 8s+ on Atlas Scale Test even with the classification cache
  above warm**, reported right after that fix shipped. Traced to `fetchViolationsMonthlyTotals` — a
  separate HTTP round-trip to the threat-detection-backend, called once for the overall trend PLUS
  once per top-5 violated asset (host-filtered) — 6 total, issued **sequentially**, each ~1-1.5s at
  this account's scale. None of this touches `classifyAllGroups`, so the earlier cache didn't help
  it at all. Fixed by firing all 6 concurrently via `CompletableFuture` (they're independent —
  nothing downstream needs one before another) instead of one after another. Verified live: a
  warm-classification-cache stats call dropped from what would've been several seconds of
  sequential HTTP to ~1.06s; page still renders correctly, no new console errors.
- **`fetchAgenticAssetsSummary`'s request payload measured ~650KB on Atlas Scale Test** — asked
  directly why `trafficMap`/`riskScoreMap`/etc. get re-POSTed on every call. Captured and inspected
  a real request body: `trafficMap`/`riskScoreMap` (5,566 entries each) and `sensitiveMap` (320
  entries) are legitimate per-request state (account-wide maps the frontend already fetches/caches
  for other pages too), but `maliciousSkillKeys` — 14,218 `"<collectionId>|<skillName>"` string
  entries, ~500KB+ — turned out to be the single largest contributor. It's server-computed data
  (`fetchAgenticSkillData`, same `AgenticObserveAction` class) that the frontend fetches once into
  its own `skillRiskScoreCache` and then had to re-POST the *entire account-wide set* back into
  `fetchAgenticAssetsSummary` on every paginated request, purely so that endpoint could check Set
  membership for the current page's ~50-100 rows. Fixed by adding `getOrBuildSkillData()` — a
  per-account cache mirroring `getOrBuildClassification`'s exact shape/TTL/sweep — and reading
  `maliciousSkillKeys` from it directly instead of the client-supplied field; removed the now-dead
  field and stopped sending it from both `AgenticAssetsPage.jsx` and `Endpoints.jsx` (their own
  `fetchAndCacheSkillApiData` calls are unchanged — still needed for `skillScoreMap`/
  `misconfiguredSkills`, which stay client-derived; only `maliciousSkillKeys` moved server-side).
  Verified live: payload dropped from ~650KB to ~220KB (confirmed via a captured request body — no
  more `maliciousSkillKeys` field, `trafficMap`/`riskScoreMap`/`sensitiveMap` unchanged), "Malicious"
  badges and the Tag filter's "Malicious Skill" branch verified still correct on both layouts (raw
  API response showed `isMalicious: true` on exactly the expected skill rows —
  `codex-workspace-sync`/`skill-creator`/`repo-onboarding-assistant`/etc.).
  **`trafficMap`/`riskScoreMap` were deliberately NOT moved server-side** — they come from
  `ApiCollectionsAction.fetchRiskScoreInfo`/`ApiInfoDao.getLastTrafficSeen`, a different Action
  class's own aggregation pipeline (itself non-trivial — the non-hardcoded-account branch does a
  full `unwind`+`sort`+`group` over `api_info`). Computing them inside `AgenticObserveAction`
  instead would mean either duplicating that pipeline or reaching across Action classes, and
  wouldn't reduce total system cost since the same aggregation has to run somewhere — it would only
  relocate who pays for it, unlike `maliciousSkillKeys` which was a genuine repeated-round-trip of
  data this same class already owns. Flagged as a real but lower-value, higher-risk follow-up if
  the remaining ~220KB (mostly these two maps) is still a problem in practice.
- **`getAllCollectionsBasic` (56MB on Atlas Scale Test) still fires when clicking a row — but only
  on the legacy layout, not the new one.** Verified both empirically: a new-layout row click only
  fires `fetchAgenticAssetDetail` (no `getAllCollectionsBasic` at all); a legacy-layout row click
  navigates to Inventory (`buildAgenticInventoryFilterForRow` + `navigate`), and Inventory
  (`ApiCollections.jsx`) has its own independent, pre-existing mount-time `getAllCollectionsBasic`
  fetch, gated by a `hasValidCache` check requiring `PersistStore.allCollections` to already be
  non-empty. That cache used to get silently warmed by `Dashboard.jsx`'s own eager
  `fetchAllCollections()` bootstrap — the exact 83MB `getAllCollections()` call removed earlier this
  round by excluding the legacy Agentic Assets route from it. Removing that eager, page-wide waste
  means Inventory can no longer assume the cache is warm, so it now pays its own (unrelated, already
  5-minute-cached) full-collections cost lazily, only when a user actually navigates there — a
  strict improvement over eagerly paying it on every Agentic Assets page load regardless of whether
  the user clicks through. Not treated as a bug to fix: Inventory's own architecture (needs the full
  account's collections to render, unless it's redesigned around its own arriving `?filters=` query
  instead of always fetching everything) is out of scope here — it's a widely-shared page used well
  beyond agentic flows, and a scoped/paginated-on-arrival rewrite of it is a real, separate,
  higher-risk initiative, not a quick patch.
- **Opening the new-layout asset flyout (screenshot: "Claude CLI", 1000 devices) unconditionally
  fetched full per-endpoint apiInfo + MCP-audit data for every one of its collections, just to
  render the Overview tab's 2 inline counts and a handful of graph nodes.** `AgenticAssetFlyout.jsx`
  fired `fetchCollectionStiBundlesBatch` (3 batched calls: `fetchApiInfosFromSTIs`/
  `fetchApiInfosForCollection`/`fetchMcpAuditInfoByCollection`) on mount for AI Agent and MCP
  Server/LLM assets, purely to compute an inline-LLM/Tool count (`buildAgentInlineTopologyComponents`/
  `buildMcpComponentsFromStis`) — and once a user opened Components, `AgentComponentsView`'s MCP-
  tools drill-down / `McpComponentsView`'s top-level list independently re-fetched the *same* 3-way
  bundle again. Traced what each downstream consumer actually reads: the risk score (`apiInfoList`)
  and MCP-audit classification (`auditRows`) only matter for the Components tab's detailed listing —
  Overview only needs endpoint *names* (does `/v1/messages*` exist? what are the distinct `/tool/*`
  names?), answerable from the STI batch alone. Added `fetchCollectionStiOnlyBatch` (1 request
  instead of 3) and switched both of the flyout's mount-time effects to it, relying on the existing
  url-based fallback bucketing (`bucketFromUrl`) to correctly classify plain `/tool/*` paths as
  "Tool" without audit data. `AgentComponentsView`'s own top-level list was already lean (server-
  paginated `fetchAgenticComponentsPage`, not this bundle at all) — only its MCP-tools drill-down and
  `McpComponentsView`'s top-level list still use the full bundle, and correctly so (genuine per-
  component risk/violations detail), just no longer duplicated by the parent's own Overview-feeding
  effect. Verified live: opening the flyout now fires only `fetchAgenticAssetDetail` +
  `fetchApiInfosFromSTIs` (zero apiInfo/audit calls); Overview's stats (1000/12/40/1/16129, matching
  the reported screenshot exactly) and Components tab both still render correctly, no new console
  errors. **Residual, smaller inefficiency (now fixed, see below)**: for MCP Server/LLM assets, the
  STI batch itself still got fetched twice if the user opened Components (once lean by the flyout,
  once full by `McpComponentsView`).
- **Follow-up to the above**: user asked directly why the flyout needed a live STI fetch at all,
  given `fetchAgenticComponentsPage` already proves the same STI data can be queried server-side,
  scoped to one asset's own collections. It can — `fetchAgenticAssetDetail` now runs the identical
  `ApiCollectionsDao.fetchEndpointsInCollection` aggregation itself (only for "agent"/"service"/"llm"
  rows; "skill" rows need neither) and returns `hasInlineLlm`/`inlineToolNames`/`mcpComponentCount`
  directly, computed with the same url-based fallback bucketing (`bucketFromUrl`/`mcpDisplayName`,
  already Java-ported for `fetchAgenticComponentsPage`) rather than shipping raw STI endpoint lists
  to the browser at all. `AgenticAssetFlyout.jsx`'s two STI-fetching effects are gone entirely,
  replaced by a synchronous `useMemo` over fields already present on the fetched asset — removed the
  now-dead `fetchCollectionStiOnlyBatch` helper and the JS `isAgentLlmMessagesUrl` utility (zero
  remaining callers). Verified live: opening the "Claude CLI" flyout (1000 collections) now fires
  only `fetchAgenticAssetDetail` — zero STI calls of any kind — with Overview stats still matching
  exactly (1000/12/40/1/16129). Cross-checked an MCP Server asset's server-computed
  `mcpComponentCount=0` against `McpComponentsView`'s independent, untouched full-detail fetch
  ("No tools, resources, prompts or skills found") to confirm the fallback bucketing agrees with
  ground truth rather than just returning a plausible-looking number.
- **Legacy Agentic Assets row-click still called `getAllCollectionsBasic` (56MB) and froze for
  several seconds with zero feedback before navigating.** User pushed back on the earlier "this is
  inherent to Inventory's architecture, out of scope" call — asked for an actual redesign: minimal-
  info requests, real pagination, and a paginated endpoint/device view on click instead of
  Inventory. Confirmed via `AskUserQuestion` that the destination should NOT be a flyout (reversing
  the earlier "keep navigate-to-Inventory" choice would have meant a flyout) — old-layout look,
  just paginated data. Built `AgenticAssetDevicesPage.jsx`: a new page (`PageWithMultipleCards` +
  `GithubServerTable`, automatic back arrow, not a flyout) showing a paginated device/endpoint table
  for exactly one asset via `fetchAgenticAssetDevicesPage` — the same endpoint the new layout's
  flyout Devices tab already uses, scoped to just that asset's own `collectionIds`, never account-
  wide. `groupKey`/`rowType`/`name`/`type` travel via query params (`?groupKey=...`); the new page
  resolves `collectionIds` itself via a lazy `fetchAgenticAssetDetail` call and shows its own
  spinner while that resolves. `Endpoints.jsx`'s `handleRowClick` now calls `navigate()`
  synchronously with no `await` first — the freeze is gone because there's nothing left to wait for
  before navigating; the destination page owns its own loading state instead. Removed the now-dead
  `buildAgenticInventoryFilterForRow`/`INVENTORY_PATH`/`INVENTORY_FILTER_KEY` imports and
  `filtersMap`/`setFiltersMap` selectors from `Endpoints.jsx` (the `constants.js` exports themselves
  are untouched — `UsersAndDevices.jsx`'s own row-click still uses them and wasn't in scope this
  round). Verified live: clicking "Claude CLI" navigates instantly (URL changes immediately), shows
  "Search in 1,000 devices" matching the asset's own count exactly, real per-device service/risk/
  traffic data, working pagination/sort/back-button, zero console errors, and zero
  `getAllCollectionsBasic`/`getAllCollections` calls anywhere in the flow (~5KB per page instead of
  56MB). **Not done**: a loading indicator on every routine sort/filter/tab-switch interaction on
  the main grid — `GithubServerTable`'s only loading affordance (`props.loading`+`loadingText`)
  replaces the entire table with a bare spinner, so wiring it into every fast (now usually <100ms
  thanks to the earlier classification cache) interaction would trade a rare multi-second freeze for
  a constant, more disruptive flash on the common case. Flagged rather than silently applied —
  worth a real design (e.g. a subtler inline/overlay indicator in the shared `GithubServerTable`
  component itself) if it's still wanted.
- **The paginated device page above was a functional regression vs. the existing production tree
  view.** User provided side-by-side screenshots: prod's `AgentEndpointTreeTable.jsx` (reached via
  Inventory's agent-tree mode) shows a two-level expandable tree — device row -> its own child
  collections, each with independent risk/sensitive/tags/skills columns, filter chips, an info
  tooltip — while the new flat page had far fewer columns and no expand/collapse. Investigated
  `AgentEndpointTreeTable.jsx` and found the reason it hadn't just been reused as-is: it's a pure
  presentational component fed entirely by `ApiCollections.jsx`'s own full `getAllCollectionsBasic()`
  fetch — the exact expensive call this round exists to eliminate. User confirmed (`AskUserQuestion`,
  "Full match (recommended)") a proper rebuild: same rich tree, sourced from new scoped/paginated
  backend data instead. Added `AgenticObserveAction.fetchAgenticAssetEndpointsPage` — Java port of
  `groupByEndpointId`/`prettifyGroupedData`/`ChildrenTable`'s grouping and tag-detection logic
  (`computeAgenticTagFlags`, new `extractSourceIdForGrouping` mirroring `splitCollectionNameForEnd
  pointSecurity`'s `sourceId` segment, reusing the existing `getOrBuildSkillData().maliciousSkillKeys`
  cache for the malicious-skill badge), scoped to one asset's own `apiCollectionIds` and paginated at
  the DEVICE-GROUP level; each returned device row carries its own `children[]` (full per-collection
  risk/sensitive/tags/skillCount) for the expanded sub-table. Rewrote `AgenticAssetDevicesPage.jsx`
  to render this via `GithubServerTable` + `CellType.COLLAPSIBLE` (confirmed via investigation that
  `GithubServerTable` supports the same `collapsibleRow`-per-row pattern `GithubSimpleTable` uses —
  they share the same `GithubRow` renderer, no `treeView` prop needed for a simple 2-level tree),
  porting `ChildrenTable`'s exact badge/config-row logic. Added an "Endpoint tags" filter chip
  (bounded 4-choice set, same `{key,label,choices}` convention as `Endpoints.jsx`'s existing "Tag"
  filter) — the one piece of prod's chip set deliberately not ported 1:1 is per-value Endpoint-ID/
  Username chips, since those are high-cardinality and would need a new distinct-values endpoint;
  left as free-text search instead (already the established `GithubServerTable` convention), not a
  silent gap. Verified live on Atlas Scale Test across both an "agent" row (Claude CLI: 4 devices,
  expand shows config/notion-mcp/claudecli/claude-cli-user(27 skills)/api.githubcopilot.com/
  mcp.razorpay.com/ai-security-docs.akto.io/claude(27 skills)/docs.akto.io/razorpay-stdio child rows,
  matching prod's structure) and a "service" row (notion-mcp: 4 devices, real usernames resolved) —
  zero console errors, zero `getAllCollectionsBasic` calls in the network log either time.
  **Follow-up self-caught during this same round**: the first pass used `fetchEndpointShieldUser
  Metadata()` (`endpointShieldHelper.js`) to resolve the Username column, which internally fires TWO
  account-wide calls — Endpoint Shield module info AND `fetchAgenticUsers` (all agentic users, for a
  team/role `userMetadataMap` this page never renders, since it has no Team/Role column). Switched to
  the leaner sibling `fetchEndpointShieldUsernameMap()`, which only fires the module-info call —
  confirmed live that `fetchAgenticUsers` no longer appears in the network log and usernames still
  resolve correctly (john.smith, liam.patel, rakshaksatsangi, jane.doe).
- **Follow-up asked again, same round: why does the asset endpoints page still call
  `getLastTrafficSeen`/`getRiskScoreInfo`/`getSensitiveInfoForCollections` at all, if the new
  `fetchAgenticAssetEndpointsPage` endpoint is supposed to be the one computing everything?** Traced
  each: `getLastTrafficSeen`/`getRiskScoreInfo` (`ApiCollectionsAction.java:1066-1139`) aggregate over
  `ApiInfoDao`, grouped by `apiCollectionId` — naturally per-collection, unscoped only because those
  endpoints take no request params; `getSensitiveInfoForCollections`
  (`ApiCollectionsAction.java:1034-1048`) aggregates over `SingleTypeInfoDao`, whose own
  `generateFilterForSubtypes` helper already accepts a `customFilter` Bson, just always called with
  `Filters.empty()`. This is the exact `[ ]` "Duplication & efficiency" bullet below
  ("Client-side full-account maps... re-POSTed in full on every grid interaction") — previously
  flagged and deferred, but for a single-asset page (vs. a list page amortizing one account-wide
  fetch across hundreds of rows) the scope mismatch is disproportionate enough to fix directly rather
  than defer again. Added `ApiInfoDao.getLastTrafficSeenForCollections`/`getRiskScoreForCollections`
  and `SingleTypeInfoDao.getSensitiveSubtypesDetectedForCollections` — each a `Filters.in
  (collectionIds)` sibling of the existing unscoped method, not a modification of it (zero risk to
  other unscoped callers). `fetchAgenticAssetEndpointsPage` now computes all three itself, scoped to
  its own `apiCollectionIds`; the frontend no longer fetches or reposts any of the three.
  `usernameMap` (Endpoint Shield) stays client-fetched — its backing `ModuleInfo` registry is bounded
  by physical device count, not collection count, and is already small/cheap regardless of scope, so
  scoping it would be new plumbing for little payoff. Verified live: identical risk/sensitive/traffic
  values before and after the change (including the misconfigured/malicious/skill-count badges on
  expanded child rows), zero console errors, and all three old calls gone from the network log.

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

- [ ] **`GithubServerTable.js` fires its initial fetch twice on every page load** (confirmed on both
  `IdentitiesPage.jsx` and the pre-existing `UsersAndDevices.jsx` — not page-specific). The
  fetch effect (`GithubServerTable.js:249-253`) depends on `[sortSelected, appliedFilters, page,
  pageFiltersMap]`; a separate effect keyed on `currentPageKey` (`:136-155`) unconditionally calls
  `setSortSelected(tableFunc.getInitialSortSelected(...))` on mount, which returns a fresh array
  literal every time regardless of whether the sort actually changed — React sees a new reference
  and re-fires the fetch effect, doubling every real-server-mode table's first network call
  (verified identical `limit`/`sortKey`/`filters` payload both times). Affects every page using
  `GithubServerTable` in real server-mode, not just the two pages rebuilt in this round.
  **Fix:** compare the new `getInitialSortSelected()` result against the current `sortSelected` by
  value before calling `setSortSelected`, or memoize/skip the call when unchanged.
- [ ] Pagination-clamp logic (`effectiveLimit`/`from`/`to`) hand-copied 6× across
  `AgenticObserveAction.java`, `NhiGovernanceViolationsAction.java`, `ModuleInfoAction.java`, with
  inconsistent default caps (50/500 vs 20/200). Extract one shared `paginate(list, skip, limit)`.
- [ ] `fetchUsersAndDevicesSummary`/`fetchDeviceEndpointsSummary` load **every** collection via
  `findAll(Filters.empty())` then classify+slice in memory on every request — not real DB-level
  pagination. `fetchAgenticAssetsSummary`/`fetchAgenticAssetsStats`/`fetchAgenticAssetDetail` had
  the exact same pattern but got a per-account short-TTL cache of the findAll+classify pass instead
  (see "Already fixed" above — a real fix for the repeat-call cost this round's user report was
  about, but still not real DB-level pagination for a COLD cache miss, which still pays the full
  O(N) cost once per TTL window). These two sibling methods don't share that cache yet and would
  hit the exact same 13-30s-at-scale problem on a large account. Two sibling endpoints in the same
  PR (`NhiGovernanceViolationsAction.fetchAllViolations`, `ModuleInfoAction.fetchEndpointShieldAgents`)
  already do real `$skip`/`$limit` aggregation; worth matching that pattern here too, or at minimum
  extending the same cache to these two methods first (much smaller change).
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
