# ATLAS rebuild — follow-up fixes (deferred from PR #6002)

Findings from the PR #6002 code review (8-angle static review + live cross-environment
verification) that are **not yet fixed**. The one confirmed issue that was fixed directly in
this branch — the `DeviceEndpoints.jsx` Group/Role/Last-Traffic field-name mismatch, plus two
related dead/redundant-state cleanups in the same file — is not repeated here.

Everything below touches files outside `DeviceEndpoints.jsx` (or is a broader architectural
concern) and is intentionally left for a separate PR. Ordered by severity; each item has enough
detail (file:line, root cause, suggested fix) to pick up cold.

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

- [ ] **Server-side asset classification is missing the NOT_ATTACHED exclusion the JS version has.**
  `AgenticObserveUtil.java:212-214` (`getTypeFromCollection`) sets `hasAiAgent = true` on any
  `ai-agent` tag presence; `mcpClientHelper.js:154` excludes tags whose value is
  `NOT_ATTACHED_VALUE` (`"not-attached"`) — the constant already exists in this same Java file,
  just not applied here.
  **Fix:** `if (Constants.AKTO_AI_AGENT_TAG.equals(tag.getKeyName()) &&
  !AgenticObserveUtil.NOT_ATTACHED_VALUE.equals(tag.getValue())) hasAiAgent = true;`

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
