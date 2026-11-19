# AI Security Posture dashboard

Single-page security posture dashboard (KPI tiles, trend charts, funnels, "Act now"
recommendations) for AI/agentic traffic. Built from an existing plan at
`~/.claude/plans/https-claude-ai-design-p-72f7e7e5-cb60-4-bubbly-ocean.md` — read that first for
the original item-by-item build spec (P0 through item 11) and its rationale.

## Request lifecycle

```
SecurityPosture.jsx (frontend)
    → api/fetchPostureSummary  {startTimestamp, endTimestamp}
        → SecurityPostureAction extends AbstractThreatDetectionAction
            → InsightDataLoader.load(ctx)              # ONE shared 60s-cached read
                → ApiCollectionsDao, ModuleInfoDao, GuardrailPoliciesDao,
                  McpAuditInfo, AgentUsersDao, UserAnalysisData,
                  + 3 threat-backend aggregations (via ThreatDetectionBackendClient)
              = InsightDataBundle bundle
            → fetchAllMaliciousEvents(rawEventFetchStartTs, trendEndTs, ..., minimalFields=true)
              = ONE raw-event fetch, reused by every trend/sparkline/biggest-movers panel
                → ThreatDetectionBackendClient.listMaliciousRequests(...)
                    → POST tbs.akto.io/api/dashboard/list_malicious_requests
                        → DashboardRouter (Vert.x) → MaliciousEventService → Mongo
            → PostureService.buildSummary(bundle, trendWindowEvents, trendStartTs, trendBoundaries)
              = one BasicDBObject response, one key per panel
            → RiskScoreCalculator (composite risk score + 5 sub-scores, reads the same bundle)
        ← JSON response
    ← renders KpiTile / StackedAreaChart / CustomPieChart / ProgressBarChart / ActionItemCard
```

**One bundle, one raw-event fetch, one response.** Every panel is a pure function over
`InsightDataBundle` + the shared `trendWindowEvents` list — never its own DB/HTTP call. This was
an explicit correction mid-build (rejected an earlier design that had 3 separate raw-event
fetches, one per KPI) — see "Design principles" below.

## File map

| Layer | File | Owns |
|---|---|---|
| Frontend page | `apps/dashboard/web/polaris_web/web/src/apps/dashboard/pages/dashboard/SecurityPosture.jsx` | Page shell, `KpiTile`, `ChartLegend`, `DataLeavingCard`, `DummyDataOverlay` |
| Frontend dummy data | `.../pages/dashboard/securityPostureDummyData.js` | Every panel's illustrative fallback data + `PANEL_EMPTY_STATE_COPY` |
| Frontend sample response | `.../pages/dashboard/res.js` | A real captured API response, useful for inspecting shape/values without a live backend |
| Backend action | `apps/dashboard/src/main/java/com/akto/action/SecurityPostureAction.java` | Reads/fetches, thread pool (`EXECUTOR`), builds the shared trend window, calls `PostureService`/`RiskScoreCalculator` |
| Backend service (panels) | `apps/dashboard/src/main/java/com/akto/service/posture/PostureService.java` | KPIs, Shadow AI trend, Data leaving, Enforcement funnel, Framework readiness, shared bucketing helpers |
| Backend service (risk score) | `apps/dashboard/src/main/java/com/akto/service/posture/RiskScoreCalculator.java` | Composite risk score + 5 sub-scores (Shadow AI / DLP / Vendor / Compliance / Threat activity) |
| Shared insights read layer | `apps/dashboard/src/main/java/com/akto/service/insights/{InsightDataBundle,InsightDataLoader,InsightContext}.java` | The one cached, account-scoped read every insights + posture feature shares |
| Shared insight helpers | `apps/dashboard/src/main/java/com/akto/service/insights/InsightUtil.java` | `canonicalVendorName`, `isPersonalAccount`, `isLocalMcp`, `serviceNameOf`, `governanceBucket`, `remarksByServiceName` |
| Route constants | `apps/dashboard/src/main/java/com/akto/service/insights/InsightRoutes.java` | Every deep link this page (or Insights) uses — never build a route ad hoc |
| Threat client (dashboard side) | `libs/utils/src/main/java/com/akto/utils/threat_detection/ThreatDetectionBackendClient.java` | HTTP client to threat-detection-backend's dashboard-facing API |
| Threat client (backend side) | `apps/threat-detection-backend/.../service/MaliciousEventService.java`, `.../router/DashboardRouter.java` | Mongo query/aggregation + Vert.x route for `/list_malicious_requests` and `/list_guardrail_violation_payloads` |
| Proto contract | `protobuf/threat_detection/service/dashboard_service/v1/service.proto` | `ListMaliciousRequestsRequest`/`Response`, incl. `minimal_fields` (field 5); `ListGuardrailViolationPayloadsRequest`/`Response` for the clause-attribution scan below |
| Shared proto (de)serialization | `libs/utils/src/main/java/com/akto/ProtoMessageUtils.java` | `toProtoMessage`/`toString` wrapping `JsonFormat` — used by essentially all proto JSON across `apps/dashboard` + `apps/threat-detection-backend` |
| Compliance sub-clause catalog | `libs/dao/src/main/java/com/akto/util/compliance/ComplianceSubClauseCatalog.java` | The fixed denominator (framework -> sub-clause list) for Framework readiness; keys must match `GuardrailComplianceSuggestionHandler.FRAMEWORK_TRIGGERS` and `ComplianceMenu.jsx#getCompliances()` |
| Clause-attribution LLM handler | `libs/utils/src/main/java/com/akto/gpt/handlers/gpt_prompts/GuardrailClauseAttributionHandler.java` | Given a framework + a batch of real violation payloads, decides which catalog sub-clauses each one hit |
| Compliance clause scan (trigger) | `apps/dashboard/src/main/java/com/akto/action/threat_detection/ComplianceClauseScanAction.java` | Fire-and-forget: submits the scan to a background executor and returns immediately — no run registry, no poll endpoint |
| Compliance clause scan (worker) | `apps/dashboard/src/main/java/com/akto/service/posture/ComplianceClauseScanService.java` | Pages violation payloads per framework, batches them to the LLM handler, upserts `ComplianceClauseCoverage`; progress (messages polled, batches completed, DB insertion success ratio) goes to `LoggerMaker`/`LogDb.DASHBOARD`, not back to the caller |
| Compliance clause coverage storage | `libs/dao/src/main/java/com/akto/dto/threat_detection/ComplianceClauseCoverage.java` + `.../dao/threat_detection/ComplianceClauseCoverageDao.java` | One doc per framework: which sub-clauses were hit, by whom (`refId`/policy name), and when — `frameworkReadiness` reads this |
| Scan trigger button | `apps/dashboard/web/polaris_web/web/src/apps/dashboard/pages/threat_detection/ThreatDetectionPage.jsx` | "Run compliance scan" button, `isEndpointSecurityCategory()`-gated, next to More Actions; fire-and-forget — no poll, the button just reports whether the trigger request itself succeeded |
| Drilldown DTO | `apps/dashboard/src/main/java/com/akto/service/posture/PostureDrillResult.java` | One level of a panel's paginated drilldown flyout — see "Paginated drilldown flyouts" below |
| Drilldown flyout (frontend) | `apps/dashboard/web/polaris_web/web/src/apps/dashboard/pages/dashboard/PostureDrillFlyout.jsx` | The one generic flyout every panel opens — breadcrumb, paginated table, URL sync |
| Drilldown AI summary | `apps/dashboard/src/main/java/com/akto/service/posture/PostureDrillNarrativeService.java` | Async AI narrative for a drill level — see "Drilldown AI summary" below |

## Design principles established this build

1. **Bucket over the page's selected date range, not a fixed lookback.** Every trend/sparkline
   (Attack attempts, Shadow AI trend, both KPI sparklines) buckets `[trendStartTs, trendEndTs]` —
   the UI's own filter — via `PostureService.trendBucketBoundaries(startTs, endTs, bucketCount)`.
   Never hardcode a "last N days" window for a trend; if the user changes the date filter, every
   number and every trend shape must change with it.
2. **One raw-event fetch, shared.** `SecurityPostureAction` computes `rawEventFetchStartTs =
   min(trendStartTs, biggestMoversStartTs)` and fetches once; each consumer (KPI sparklines,
   biggest movers) filters/buckets that one list itself rather than re-fetching with its own
   window. Copy-pasting a second raw-event fetch for a new panel is the anti-pattern to avoid —
   it was called out explicitly as not "senior engineer level" when it first appeared.
3. **Never render a bare zero.** Every panel that lacks real data attaches a `Gap` (see below) and
   the frontend shows either an empty-state message or a blurred `DummyDataOverlay`, never a
   silently-wrong `0`.
4. **Ask before guessing on ambiguous bucketing/semantics.** When a panel's definition is
   genuinely underdetermined by the data available (e.g. what "coverage over time" even means
   when only a point-in-time ratio exists), stop and ask rather than pick a proxy unilaterally.

## The `kpi()` / gap pattern (PostureService.java)

Every KPI builds a `BasicDBObject` via a shared `kpi(id, label, value, delta, route)` factory,
then may call `addGap(kpi, gapId, reasonId, humanImpactMessage)` when data is missing/unavailable.
`GAP_*` / `REASON_*` constants live at the top of `PostureService.java`. Known gap categories in
use: `GAP_DEVICE_IDENTITY`, `GAP_POSTURE_HISTORY`, `GAP_THREAT_BACKEND`, `GAP_NO_PRIOR_WINDOW`,
`GAP_GUARDRAIL_POLICIES`, `GAP_COMPLIANCE_SCAN` (Framework readiness: no compliance-clause scan has
run yet — distinct from `GAP_GUARDRAIL_POLICIES`, which means no policy even has a framework
mapped). Add a new `GAP_*` rather than reusing one that doesn't semantically fit — the frontend's
`PANEL_EMPTY_STATE_COPY` keys off these.

## Shared bucketing helpers (PostureService.java)

```java
// Evenly divides [startTs, endTs] into bucketCount ascending boundaries (last snaps to endTs).
public static List<Integer> trendBucketBoundaries(int startTs, int endTs, int bucketCount)

// Counts DashboardMaliciousEvents matching `matches` per half-open (prevBoundary, boundary] bucket.
// DashboardMaliciousEvent-specific — NOT generic. A trend over a different entity type
// (e.g. ApiCollection.startTs) needs its own loop; see shadowAiTrend for that pattern.
static List<Long> bucketedSparkline(List<DashboardMaliciousEvent> events, int rangeStartTs,
                                     List<Integer> boundaries, Predicate<DashboardMaliciousEvent> matches)
```

`TREND_BUCKET_COUNT = 8` (Attack attempts trend + both KPI sparklines). `SHADOW_TREND_BUCKET_COUNT
= 12` (Shadow AI trend only, cumulative sanctioned/unsanctioned by `ApiCollection.startTs`).

`shadowAiTrend()` is the template for any future "bucket by asset first-seen" trend: it does NOT
call `bucketedSparkline` (wrong type), it reads `bundle.collections` (already loaded, no new DAO
call — `ApiCollectionsDao` has no dedicated time-range query; every consumer buckets the
already-loaded in-memory list itself), and buckets cumulatively (`firstSeen <= boundary` → count
into every bucket from there onward), not into a single exclusive bucket.

## `ApiCollection.startTs` ("firstSeen")

- Field: `int startTs` (epoch seconds), constant `ApiCollection.START_TS`.
- Stamped exactly once, at `Context.now()`, the moment a host/collection is first discovered from
  live traffic (`HttpCallParser.java`) — never updated again afterward.
- `bundle.collections` is loaded unbounded (no time filter) by `InsightDataLoader`, projected to
  include `START_TS`; every firstSeen-bucketed feature (`shadowAiTrend`,
  `WhatChangedThisWeekProvider`) filters that same in-memory list rather than querying Mongo again.
- There is **no** ApiCollection-level "is this covered by a policy" field/link. Guardrail policy
  scoping onto hosts/collections (not devices) is a *separate* targeting axis from
  `applyToDeviceIds`: `GuardrailPolicies.applyToAllServers` / `selectedMcpServersV2` /
  `selectedAgentServersV2` / `selectedLlmServersV2` (+ their `negated*` flags), matched by
  hostname. `PolicyHygieneProvider` / `GuardrailCoverageGapProvider`
  (`apps/dashboard/src/main/java/com/akto/service/insights/providers/`) already implement a
  host-based "does any policy cover this host" check — check there before writing a new one if a
  future panel needs collection/host-level (not device-level) coverage.

## Paginated drilldown flyouts

Five panels (Shadow AI trend, What data is leaving, Enforcement funnel, Vendor risk, Framework
readiness) each open a flyout showing the real rows behind their aggregate, instead of
`navigate()`-ing away to an unrelated existing page. One generic mechanism backs all five — see
the plan this was built from for the full per-panel design
(`~/.claude/plans/https-claude-ai-design-p-72f7e7e5-cb60-4-bubbly-ocean.md` at the time of that
build; this file is what survives long-term).

```
SecurityPosture.jsx: openDrill(drillId, path?) → ?drill=<id>&path=<a/b/...> (query params, {replace:true})
    → PostureDrillFlyout.jsx (generic — every panel opens this same component)
        → dashboardApi.fetchPostureDrill(drillId, path, startTs, endTs, skip, limit)
            → SecurityPostureAction#fetchPostureDrill — its own bundle load + raw-event fetch,
              same shape as fetchRiskScoreBreakdown (a second on-demand call, not threaded through
              fetchPostureSummary)
                → PostureService.fetchDrill(...) dispatches on drillId to a private handler
                  (RiskScoreCalculator.vendorRiskDrill for DRILL_VENDOR_RISK; PostureService's own
                  for the other four)
        ← PostureDrillResult (title, breadcrumb[], columns[], rows[], drillable, total/skip/limit, ctas[], dataGaps[])
    ← FlyoutBreadcrumb (breadcrumb) + AgGridTable (paginated rows, serverSideRowModel)
```

Two levels per panel: an empty `path` returns a group/category list (tools, data types, funnel
stages, vendors, frameworks); a one-segment `path` drills into that group's members (a tool's
devices, a data type's incidents, a stage's events, a vendor's devices, a framework's clause
hits). `path` is generic (slash-joined segments, arbitrary depth) even though nothing goes past
one segment today — a third level for a specific panel is a new case in that panel's handler, not
an architecture change.

**Every row's data was already sitting in memory** by the time the existing aggregates run
(`bundle.collections`, the shared `trendWindowEvents`, `bundle.policies`,
`ComplianceClauseCoverageDao.findAllCoverage()`) — confirmed before building this, so every
handler is pagination logic over an in-memory list (`PostureService.paginate`), not a new Mongo
query or threat-backend call, for current data volumes.

**The date range is now in the URL too** (`?since=&until=`, raw epoch seconds) — this page's
`currDateRange` used to be pure local `useReducer` state with no URL reflection at all, which
would have made a shared drilldown link reproduce the wrong window on reload. Seeded once on mount
from `searchParams` (falls back to the page's original default, "Last 30 days"), mirrored on every
change with `{replace: true}`. This follows the same `since`/`until` convention
`ThreatDetectionPage.jsx`/`CompliancePage.jsx` already use elsewhere in this app — not a new
pattern.

`PostureDrillResult` reuses `InsightResult.Metric`/`Cta`/`Gap` directly rather than a parallel DTO
family (each row's `id` field is what becomes the next `path` segment when `drillable` is true).
It does **not** reuse `InsightResult.Evidence` — Evidence is a fixed-cap "rows capped, but here's
the true total" shape with no skip/offset concept, a different contract than this drilldown's real
paging.

## Per-panel status (real vs. illustrative)

| Panel | Status | Notes |
|---|---|---|
| Critical alerts KPI | **Real**, incl. sparkline | `bucketedSparkline` over `trendWindowEvents`, severity=CRITICAL |
| Sensitive data incidents KPI | **Real**, incl. sparkline | joins on `category` (not `subCategory` — see gotchas) against PII policy names |
| Monitoring coverage KPI | **Real value**, sparkline **not implemented** (always blurred dummy) | Value = covered devices / live devices, point-in-time only, no history stored (`GAP_POSTURE_HISTORY`). Trend was being (re)designed to build off `ApiCollection.startTs` — pick this up per "Open work" below. |
| AI risk score KPI | Real composite; drilldown trend not built | 5 weighted sub-scores in `RiskScoreCalculator`; vendor risk sub-score has no data source yet (see item 11 of the plan) |
| Shadow AI trend | **Real**, bucketed by `ApiCollection.startTs`; **drilldown built** | template for any future firstSeen-bucketed trend; drill: tool list → that tool's devices |
| What data is leaving (donut) | **Real**; **drilldown built** | category→policy join via `PostureService.matchedPolicyCounts`; legend order/colors fixed to a specific `SEGMENT_COLORS` map — do not reorder; drill: data-type list → that type's incidents |
| Attack attempts + biggest movers | **Real** | shares `trendWindowEvents`; biggest movers filters its own `windowStartTs` (30d) out of the same list; no drilldown built |
| Act now | Real (reuses existing `InsightsAction`/`InsightsFlyout`) | |
| Framework readiness | **Real** once a scan has run; blurred dummy until then; **drilldown built** | clausesCovered/totalClauses per framework from `ComplianceClauseCoverageDao`, written by `ComplianceClauseScanService`'s LLM scan of real violation traffic (triggered from the Threat Detection page); requires `apps/threat-detection-backend` to be redeployed for `list_guardrail_violation_payloads` — see gotchas below; drill: framework list → that framework's clause hits |
| Enforcement funnel | Partially real; **drilldown built** | "matched a policy" / "hard-blocked" / "warned" real; "warning overridden" has no data source (`guardrailAction` field is declared but never populated — item 10 of the plan), and its own drill level reports that gap instead of an empty/wrong table; drill: stage list → that stage's events |
| Vendor risk bubble chart | Chart not built, blocked on product decision; **drilldown built off the same underlying data** | no vendor entity exists in Akto at all, but `RiskScoreCalculator.vendorRiskAnalysis`'s own vendor/device grouping is real — drill: vendor list → that vendor's devices, opened from a click anywhere on the (currently static-scatter) panel body |

## Drilldown AI summary

Every drilldown level (root or one segment deep, any of the 5 panels) also gets an AI-written
narrative — same {@code InsightNarrativeHandler} + {@code InsightNarrativeCacheDao} InsightService
already uses for insight detail narratives (see that class's own `generateAndCacheNarrative`),
reused wholesale rather than a parallel prompt/cache. `PostureDrillNarrativeService` is the one new
piece: it builds the same metrics/evidence/caveats/dataGaps input shape from a
`PostureDrillResult`'s own `summary`/`rows`/`dataGaps` (instead of an `InsightResult`'s), with no
`draftConcern`/`Impact`/`Remediation` — a drill has no provider-computed draft to ground, unlike an
insight.

```
SecurityPostureAction#fetchPostureDrill (after PostureService.fetchDrill(...) returns)
    → PostureDrillNarrativeService.attachNarrative(result, accountId, drillId, path)
        cache hit  → attaches narrativeStatus=OK + markdown/concern/impact/remediation, synchronously
        cache miss → narrativeStatus=PENDING, submits generation to its own background executor,
                      returns immediately (never blocks the HTTP response on the LLM call)
    ← PostureDrillResult (now carrying narrativeStatus/narrativeMarkdown/narrativeConcern/
      narrativeImpact/narrativeRemediation, same field names/semantics InsightResult uses)
PostureDrillFlyout.jsx: renders a DrillNarrative box (same visual language as
InsightDetailView.jsx's "Analysis" section) and, when PENDING, re-fetches the same drillId/path
every 3s (capped at 5 tries) to pick up the cache once the background job lands — merges in only
the narrative fields, never touching the table's own SSRM-managed rows/pagination.
```

Deliberately **not** part of `PostureService`: that class's whole design premise (see its own
javadoc) is a pure function over its inputs — no Mongo, no HTTP — so its unit test suite never
touches a real cache or a real LLM call. `PostureDrillNarrativeService` is the one place in this
package allowed to do either, the same split `InsightService` (orchestration + narrative) already
draws against its own pure `InsightProvider`s.

Skips generation entirely (stays `UNAVAILABLE`, no LLM call) when a level has nothing to narrate —
no summary metrics, no rows, no data gaps. A failed generation is deliberately left **uncached**
(not even an UNAVAILABLE sentinel) so the next request for that same level just retries, rather
than wedging a transient failure (a rate limit, a timeout) in place for the cache's 7-day TTL.

## Risk score breakdown is now a 6th drill, not a separate flyout

The composite risk score KPI's own detail (5 sub-scores + "what moved the score") used to be a
standalone `FlyLayout` backed by its own `fetchRiskScoreBreakdown` action — no breadcrumb, no URL
sync, no AI summary. It is now `PostureService.DRILL_RISK_SCORE` ("riskScoreBreakdown"), folded
into the exact same `PostureDrillFlyout`/breadcrumb/URL mechanism the other 5 panels use:

```
KpiTile (risk score) onOpen → openDrill(DRILL_RISK_SCORE)  → ?drill=riskScoreBreakdown
    → PostureDrillFlyout — root level (path="") renders RiskScoreRootBody (the old FlyLayout body:
      ring/value/delta, always-blurred trend, 5 sub-score rows, "what moved the score"), NOT the
      generic AgGridTable every other drill/level uses — see PostureDrillResult#riskScoreBreakdown
    → each sub-score row is clickable → openDrill(DRILL_RISK_SCORE, subScoreId) → level 2
        shadowAiExposure → delegates to shadowAiDrill's own root level (same table as DRILL_SHADOW_AI)
        vendorRisk       → delegates to RiskScoreCalculator.vendorRiskDrill's own root level
        dlpIncidents / threatActivity → their own small handlers in PostureService (pagination
          over RiskScoreCalculator's *AllDevices methods)
        complianceGaps   → a framework list (frameworkCoverageRows — the same rows Framework
          readiness's own root level shows), not the uncovered-policy list this used to be
    → every level-2 row is ALSO clickable → openDrill(DRILL_RISK_SCORE, `${subScoreId}/${entityId}`)
      → level 3, one "profile" page per entity — see the next section
```

Dispatched from its own `PostureService.fetchRiskScoreDrill(...)`, not a 6th case in `fetchDrill`'s
switch — its inputs genuinely differ (needs the CURRENT window's full raw-event list plus a real
prior-window comparison, not `trendWindowEvents`). `SecurityPostureAction#fetchPostureDrill`
branches on `DRILL_RISK_SCORE` to do those fetches (the same ones the old `fetchRiskScoreBreakdown`
action used to do) instead of the trend-window fetch the other 5 drillIds share.

`riskBand`/`RiskScoreRing`/`DELTA_TONE_TO_COLOR`/`formatDelta`/`DummyDataOverlay`/`GapHint` moved
to `new_components/PostureShared.jsx` — both `SecurityPosture.jsx`'s own cards (KpiTile, every
`DummyDataOverlay` user) and `PostureDrillFlyout.jsx`'s relocated risk-score body need them; a
one-directional import (PostureDrillFlyout → PostureShared) avoids the circular import a direct
`PostureDrillFlyout → SecurityPosture` import would have created (SecurityPosture already imports
PostureDrillFlyout).

## Risk score breakdown's own 3rd level (entity profiles)

All 5 sub-scores now go one level deeper than their own group/category list: a tool, a device/
employee, a vendor, or a framework, each rendered as a "profile" page rather than the generic
paginated `AgGridTable` every other level uses.

```
Level 2 row click → openDrill(DRILL_RISK_SCORE, `${subScoreId}/${entityId}`)
    → PostureService.fetchRiskScoreDrill's 2-segment branch dispatches to one of:
        shadowAiExposure/<tool>   → shadowAiToolProfileDrill      (PostureService)
        dlpIncidents/<deviceId>   → dlpDeviceProfileDrill         (PostureService, wraps deviceProfileDrill)
        threatActivity/<deviceId>→ threatDeviceProfileDrill       (PostureService, wraps deviceProfileDrill)
        vendorRisk/<vendor>       → RiskScoreCalculator.vendorRiskProfileDrill
        complianceGaps/<framework>→ complianceFrameworkProfileDrill (PostureService)
    ← PostureDrillResult with layout="profile", subtitle, badge {label, tone}, notice, facts[],
      sections[] ({id, title, subtitle, kind: "timeline"|"table", columns[], rows[], total}) — see
      PostureDrillResult#layout's own javadoc. Each section's rows are capped server-side at
      PostureService.PROFILE_SECTION_CAP (50), not skip/limit-paginated — total keeps the real
      count for a "showing N of M" footer.
    → PostureDrillFlyout.jsx: DrillProfileBody (ProfileHeader/ProfileStats/ProfileFacts/
      ProfileTimeline/ProfileTable) instead of AgGridTable; the top-of-flyout DrillStats/ctas row
      is skipped for this layout (the profile's own header already shows the CTAs)
```

DLP incidents and Threat activity share one device/employee profile (`deviceProfileDrill`) — same
page (an "AI activity timeline" over that device's own events, plus the coaching-not-conclusions
notice from the mock this was built from), different event source per sub-score (PII-filtered
`allThreats` vs. every threat-backend event in the window). Shadow AI exposure, Vendor risk and
Threat activity all need a window-event list that neither the risk-score root nor `dlpIncidents`
already fetch — `SecurityPostureAction#fetchPostureDrill`'s `DRILL_RISK_SCORE` branch computes
`needsWindowEvents` and, when true, fetches with the same `trendStartTs`/`trendEndTs` (same
`TREND_BUCKET_COUNT`-based fallback, `minimalFields=true`) the non-risk-score drillIds' own
`trendWindowEventsFuture` already uses. Compliance gaps needs no events at all (it reads
`ComplianceClauseCoverageDao` and `bundle.ctx.getStartTs()/getEndTs()` for the window, same as
`frameworkReadinessDrill`).

**Only shadowAiExposure's own entity-level fetch is scoped server-side** (an `apiCollectionId`
filter via `additionalFilters` — a filter `MaliciousEventService#buildQueryFromFilter` already
supports, no backend change), computed by `PostureService.shadowAiToolCollectionIds` (a public
passthrough to `RiskScoreProfileDrillService#collectionIdsForTool`, needed because
`SecurityPostureAction` — a different package — must build this filter *before* calling
`fetchRiskScoreDrill`, and `RiskScoreProfileDrillService` is package-private like
`RiskScoreCalculator`). This is provably safe only for this one sub-score:
`shadowAiToolProfileDrill`'s own `toolEvents` filter already only counts an event whose host
belongs to a non-deactivated collection for that tool, so scoping the fetch to those same
collection ids changes nothing about which events end up counted — it just moves the identical
filter earlier, into the query itself, instead of an unfiltered fetch filtered client-side.
**Vendor risk and Threat activity deliberately stay unfiltered** — do not "fix" this the same way
for them: a vendor match (`InsightUtil.endpointVendorNameOfHost`) is a host-string pattern
independent of whether a live `ApiCollection` still exists for it (a deactivated collection's past
malicious events are still real vendor-risk signal), and Threat activity's `hostSeverityCounts` has
no `ApiCollection` linkage at all — scoping either to a known-collection-id list would silently
under-count real, currently-shown activity. When `shadowAiToolCollectionIds` comes back empty (the
tool genuinely has zero live collections right now), the action skips the fetch entirely rather
than passing an empty `apiCollectionId` list — an empty list is silently ignored by
`buildQueryFromFilter` (falls through to no filter at all), which would otherwise turn "nothing to
show" into an accidental unfiltered fetch, the exact cost this scoping exists to avoid.

**Compliance gaps' L3 status** (Met/Partial/Gap) is computed per catalog sub-clause, not per
policy: **Gap** when nothing hit that sub-clause in the window; **Partial** when something did but
no hitting policy's `llmRule.compliance` map actually keys on THIS specific framework (checking
`policyHasComplianceMapping` alone only proves "mapped to *some* framework"); **Met** when at least
one hit came from a policy mapped to this exact framework. The sub-score's own composite VALUE is
unaffected by any of this — only what the L2/L3 tables show changed.

**Epoch fields, fixed at both ends of this build**: `firstSeen`/`lastSeen`/`lastScannedAt`/
`timestamp` columns used to render as bare epoch seconds in every drill table — `PostureDrillFlyout.jsx`'s
`EPOCH_FIELDS` set now routes all of them (not just `detectedAt`) through `func.prettifyEpoch` via
`EpochCell`, and profile `Fact`s are pre-formatted server-side by `PostureService.humanEpoch`. The
AI narrative had the same bug (an LLM asked to describe a raw epoch integer would just print it) —
fixed in the **prompt only**, not with a new structured field: `InsightNarrativeHandler.buildPrompt`
adds a HARD RULE naming the epoch-shaped keys and a `CURRENT_TIME` orientation line, instructing the
model to describe timing in relative, qualitative words ("recently", "on its most recent
occurrence") and never compute or state a specific date/day-count — computing a day-count would
itself violate rule 1 ("never compute a number"), which is why this couldn't be "state the actual
date" instead. `PROMPT_VERSION` bumped 4→5 (invalidates old cached narratives in both
`InsightService`'s and `PostureDrillNarrativeService`'s fingerprints) and `PostureDrillNarrativeService`'s
own `DRILL_NARRATIVE_INPUT_VERSION` bumped 1→2 (a profile level's `facts`/`sections` are now folded
into the same `metrics`/`evidence` shape the narrative input builds, since a profile response has no
top-level `rows` for the old shape to find).

**Fields with no real data source were deliberately omitted, not invented or stubbed**: the
original mocks this level was built from also showed Manager/Training/Departments/Certifications/
Contract/Sub-processors facts and Open case/Schedule check-in/Block/Create task buttons — none of
these have a backing entity in Akto today. Every profile's `facts`/CTAs only ever surface real,
already-loaded data; CTAs route to existing `InsightRoutes` destinations only.

## Critical alerts / Sensitive data incidents — the KPI tiles' own drilldowns

Two of the four KPI tiles now open an in-context drilldown too (Monitoring coverage stays a plain
tile; Risk score already had its own — see above), reusing `PostureService.fetchDrill`'s existing
generic mechanism rather than the risk-score-only "profile" one — `DRILL_CRITICAL_ALERTS`/
`DRILL_SENSITIVE_DATA` are just the KPI's own `id` reused as its drill id (`PostureService.
DRILL_CRITICAL_ALERTS = KPI_CRITICAL_ALERTS`), since there's no second name to keep in sync.

```
KpiTile (Critical alerts / Sensitive data incidents) onOpen → openDrill(id) → forceClickable
    (SecurityPosture.jsx: Monitoring coverage is the only KPI still on the old navigate-away openKpi)
```

**Critical alerts** (root-only, not drillable): the top `CRITICAL_ALERTS_TOP_N` (5) CRITICAL-
severity events in the window, newest first — a fixed snapshot, not a real paginated list.
`total` is deliberately set to `rows.size()` (== 5 once capped), NOT the real total matching count
— AgGridTable's own SSRM would otherwise see `total` bigger than what's on this one page and
request a further one, and this handler ignores skip/limit entirely (always the same fixed top-N),
so a "page 2" would come back as the identical 5 rows again. The real total is still there, just
moved to its own `summary` metric ("Critical events in window") instead of driving pagination. Its
own CTA ("View all critical alerts") is the actual "see everything" escape hatch, to
`InsightRoutes.GUARDRAIL_VIOLATIONS` with `params={severity: "CRITICAL"}`.

**That `summary` metric is `sumCritical(bundle.hostSeverityCounts)` — the exact same aggregation
`criticalAlertsKpi` computes for the tile's own headline value — never a recount over
`trendWindowEvents` (the raw, separately-fetched threat-backend event list this drill's rows are
built from).** The two counts are known to disagree, sometimes by a large factor: a window can
carry far more raw "malicious event" rows per host than `hostSeverityCounts`' own per-host critical
count reflects — the same bursty/near-duplicate-events shape `RiskScoreProfileDrillService
.clusterEvents` already exists to collapse for a device's own timeline (see the Shadow AI/DLP/
Threat activity profile section above). Since `hostSeverityCounts` is the number already sitting
on the KPI tile the user clicked to get here, it's the one authority this drill must report back —
recomputing a second, independent count from the raw list is exactly how the two silently drifted
apart in the first place (caught when a real total of 4326 showed up under a tile reading 1847).
`sensitiveDataDrill` has no equivalent single-number fix available for its own per-tool/per-user
row counts: `sensitiveDataIncidentsKpi`'s own value comes from `bundle.subCategoryCounts` (a
per-category aggregation, no host/tool dimension at all), so there is no authoritative source to
reconcile a *per-tool* breakdown against — grouping by tool is a new axis the KPI's own aggregation
simply cannot answer, unlike Critical alerts' `hostSeverityCounts` which is already per-host.

**Sensitive data incidents** is a real 2-level drill: root groups the window's PII-policy matches
(the exact same `piiPolicyNamesLower` join `sensitiveDataIncidentsKpi` itself already uses, so the
two can't disagree) by agent/tool (`toolForHost` — a host-string-only version of
`governanceGroupingName`, since a raw threat-backend event has no `ApiCollection` to look the
grouping up on); one segment in is that tool's own users (resolved the same `deviceIdToUsername`
way every other per-device table on this page already does).

**A CTA's own `params` now actually do something** — `PostureDrillFlyout.jsx`'s `ctaHref` appends
them as a URL query string before navigating, not router `state` (this was the bug this build
found and fixed): the destination pages this app already has (`ThreatDetectionPage.jsx`'s own
severity filter, for one) read their own pre-filters off `searchParams`, never off
`location.state` — a CTA's `params` passed as `state` would have silently landed nowhere on
arrival. `SecurityPosture.jsx`'s own pre-existing `openKpi`/`kpi.linkParams` (still used by
Monitoring coverage) has this exact same bug and was NOT fixed — it's a direct-navigate path
outside this drilldown flyout, out of scope for this pass; flag it if it starts mattering.

**The shared "ground the AI summary in real traffic" mechanism, used by both drills identically**:
both drills' rows carry a hidden `refId` (not a displayed column) — Sensitive data incidents' own
per-user rows also carry a hidden `policy`, naming that user's own most recent PII hit's policy.
`SecurityPostureAction#attachEvidenceSamples` (called right after `fetchDrill` returns, before the
narrative is attached) takes the first `EVIDENCE_ENRICHMENT_ROW_CAP` (5) rows, and — for any that
have a refId — calls `ThreatDetectionBackendClient.listGuardrailViolationPayloads` (the SAME
existing, already-deployed endpoint `ComplianceClauseScanService` uses for the compliance scan;
no new proto field, no backend redeploy) to fetch each one's own real intercepted request/response
sample ("latestApiOrig"), matched back by refId, truncated to `EVIDENCE_SAMPLE_MAX_CHARS` and
attached as a new `evidenceSample` field on that same row map. `PostureDrillNarrativeService`'s own
`buildNarrativeInput` already serializes a drill's rows verbatim into the LLM's EVIDENCE block, so
this one attach is the *only* change needed to ground the narrative in real traffic instead of just
aggregate counts — no narrative-service changes. `InsightNarrativeHandler`'s prompt gained one hard
rule (8) telling the model to prefer `evidenceSample` + that row's own `policy` when explaining why
a row matters, and to paraphrase rather than quote it at length (`PROMPT_VERSION` bumped 5→6). A
row with no refId, or a backend that doesn't respond, silently no-ops — the narrative still gets
everything else.

## PostureDrillFlyout — one scroll region, number cards

Frontend-only build (`PostureDrillFlyout.jsx` / `PostureShared.jsx`), no backend change.

**The bug**: every level (`RiskScoreRootBody`, `DrillProfileBody`, the AI summary) used to wrap
itself in its own `<Scrollable>`, and `AgGridTable` was pinned to `domLayout="normal"` inside a
pixel-height flex chain — three-plus independently-scrolling panes stacked in one flyout, each
fighting the others for a slice of a fixed total height. A tall stats/gaps strip could squeeze the
table down to a sliver, or the table could eat the space the AI summary needed, depending on how
much content each level happened to have that render.

**The fix**: exactly one `<Scrollable>`, directly under `AgenticFlyoutShell`'s own fixed-height/
`overflow:hidden` child wrapper, holding everything (stats+CTAs+gaps, the level's own body,
narrative) as plain `VerticalStack`/`Box` document flow — no nested `flex:1, minHeight:0` chains
below it. `RiskScoreRootBody`/`DrillProfileBody` no longer wrap themselves in `Scrollable`/`Box`;
they just return their content, since the one outer `Scrollable` already provides both the padding
box and the scroll region. `AgGridTable` switched from `domLayout="normal"` to `"autoHeight"` (that
prop's own default, actually — this file was overriding it) — with no explicit `height` prop and no
fixed-height ancestor forcing it, the grid sizes itself to its own rows/pagination footer and folds
into the same single scroll, instead of carrying its own internal scrollbar. Every drill/level here
is already capped small (`DEFAULT_DRILL_LIMIT`/`CRITICAL_ALERTS_TOP_N`/`PROFILE_SECTION_CAP`), so
autoHeight was always the safe choice — nothing here is an unbounded, thousands-of-rows grid.

**Number cards** — `PostureShared.jsx` gained `NumberCard`/`NumberCardsRow`: a drill's `summary[]`
(`InsightResult.Metric`, real backend numbers) now renders as bordered tiles (`DrillStats`' own
stats row, and a profile page's `summary` separately from its qualitative `facts`) instead of plain
label/value text, matching this page's existing "big number over a subdued label" tile language.

A client-side "count/share by X" pie chart (built off `drill.rows`/a profile section's own rows,
picking the first column as the label by default) was tried and **removed** — the "first column is
always the right label" assumption doesn't hold generally and needs a proper per-drill design
pass, not a generic heuristic. If this gets revisited, don't just restore `categoryBreakdown`/
`pickChartColumns`/`CategoryPieCard` verbatim (removed from `PostureShared.jsx`) — reconsider the
column-picking rule first.

## Known gotchas (hit or nearly hit this build)

- **`InsightUtil.canonicalVendorName(String)` returns display-cased names** ("OpenAI",
  "Anthropic"). Any comparison against an allowlist set must lowercase both sides — two call sites
  (`InsightDataLoader.loadAllowlistNames`, `RiskScoreCalculator.vendorRiskAnalysis`/
  `vendorRiskTopUnapprovedDevices`) were bitten by this and fixed. Grep for `canonicalVendorName`
  before adding a new consumer.
- **`applyToDeviceIds == null` means "applies to ALL devices"; empty list means "applies to
  none."** A falsy/`isEmpty()` check inverts the coverage result. See
  `monitoringCoverageKpi`'s javadoc.
- **Join on `category`, not `subCategory`**, when matching `ThreatCategoryCount`/event category
  against a policy name (`sumMatching`, `matchedPolicyCounts`). This bug recurred twice in this
  build (Compliance gaps, then Sensitive data incidents) — same fix pattern both times.
- **`InsightContext.bundleCacheKey()` must include `startTs`/`endTs`.** Without the range in the
  key, a 60s-cached bundle silently serves the *previous* date filter's numbers when the user
  switches ranges within that window.
- **Thread-local `Context`.** `InsightDataLoader`/`InsightService` re-set
  `Context.accountId`/`userId`/`contextSource` inside every worker task. Any new fan-out
  (`EXECUTOR.submit(...)`) must do the same or silently read/write the wrong account.
- **`AgentUsersDao#findByUserIdsOrUserNames` returns un-deduped docs** — multiple docs per
  username. Any distinct-user count must dedupe explicitly.
- **`fetchPiiEvents`/`fetchViolationEvents` return `null` under `LIST` scope** — must not be read
  as zero. Use `InsightProvider.Scope`, don't invent a parallel enum.
- **`JsonFormat.parser()` throws by default on unrecognized JSON fields.** If a client sends a
  proto field a not-yet-rebuilt server doesn't know about (e.g. `minimalFields`, added this
  build), the parse fails and `ProtoMessageUtils.toProtoMessage` returns `Optional.empty()` —
  which every caller treats identically to "no data," not "error." This surfaced as a
  hard-to-diagnose universal flatline (KPI sparklines, biggest movers all silently empty) until
  `ThreatDetectionBackendClient`/`ProtoMessageUtils` were given logging (see below). If you add a
  new proto field a live-deployed service must recognize, that service needs a matching redeploy
  — this is an operational step, not something the caller can defend against alone.
- **`ProtoMessageUtils.toString`/`toProtoMessage` used to silently swallow all exceptions**
  (`catch (Exception e) { // Ignore }`). Now logged via `LoggerMaker` with the truncated payload —
  keep this logging; it is the only place in the whole proto-JSON chain that still has the
  original exception message.
- **Maven incremental compile can false-positive.** `mvn -pl apps/dashboard -o compile
  -DskipTests -q` can report "Nothing to compile" success against stale `target/classes`. Always
  `rm -rf target/classes` before trusting a compile check that matters.
- **Multi-module dependency: `libs/utils` must be `mvn install`ed** (not just `compile`d) before
  `apps/dashboard`/`apps/threat-detection-backend` pick up its changes.
- **Pre-existing, unrelated bug, flagged not fixed:** `InsightDataBundle
  #fetchMaliciousComponentInvocations` passes the literal account id `1779231193` instead of
  `ctx.getAccountId()` — a cross-account data leak in a shipped feature, out of scope for this
  page but worth its own fix.
- **`AgGridTable`'s `filterStateUrl` prop is a Zustand persistence key, not a URL-query-param
  sync** — despite the name, it scopes which entry of `PersistStore.filtersMap` a table's
  filter/sort state is saved under (keyed by `window.location.pathname` if omitted), so every
  `AgGridTable` on the same route collides on the same persisted state unless given a distinct
  key. `PostureDrillFlyout` passes `` `security-posture-drill/${drillId}/${path}` `` for exactly
  this reason — omit it and drilling into a second panel would silently reuse the first panel's
  sort/filter state.

## Argus (AGENTIC) insights — 5 posture insight cards

**This replaced an earlier per-agent "Top Posture Findings" design** (`InsightId.Group.ARGUS_POSTURE`,
6 `InsightProvider`s, `InsightResult.Finding`, `AgentIndex`/`AgentRef`, `AgenticNarrativeStrategy`/
`AgenticInsightNarrativeHandler`) that shared the `service/insights/**` provider framework Atlas
Discovery uses. That whole framework was deleted after a live-data review found two real
aggregation bugs (missing `count` in `AgentFindingGroupAggregation`'s `$project`, `excludeId()` on
a facet whose Java side still read `_id`) and, once those were fixed and the data was actually
visible, a decision that per-agent findings weren't the right shape for this page at all — see git
history on this file/`InsightDataLoader.java`/`InsightService.java` for the removed design if a
future need resembles it closely enough to reuse. The read-layer DAO methods those providers used
(`TestingRunResultSummariesDao#summaryIdsInWindow`, `VulnerableTestingRunResultDao#redTeamAggregates`,
`McpAuditInfoDao#auditGroupsForAgents`, `AgentConversationResultDao#findValidatedSummaries`) were
**left in place, unused** rather than deleted — they're real, working, generically-useful DAO
methods, not "provider" architecture, and conversation-grounded red-team detail is a plausible
future need for a drill-down. `AgentFindingGroup`/`AgentFindingGroupAggregation`/`DailyCount`
(`libs/dao/.../dto/insights/agentic/`, `.../dao/insights/agentic/`) are still live — the current
red-team cards below use `AgentFindingGroup` directly.

Current design: **5 fixed cards**, each a small Java-computed data section plus a separately-loaded
AI summary — no `InsightProvider`/`Finding`/per-agent list involved. Mirrors `buildSummary`'s own
"pure function over pre-fetched data" shape (see the top of this file), just for a different
section of the same page. Frontend: `AgenticPosture.jsx`'s "Insights" section, backed by
`overview/InsightCardsSection.jsx`.

```
AgenticPosture.jsx
    → api/fetchArgusPostureInsights   (fast — no LLM call)
        → ArgusPostureAction#fetchArgusPostureInsights
            → InsightService#getOrLoadBundle(ctx)              — collections/policies only
            → TestingRunIssuesDao#openIssueGroupsForDashboard  — red-team open issues
            → InsightService#fetchArgusMaliciousEvents         — guardrail/malicious events
            → SearchClientFactory#fetchAgenticServiceObservability     — per-agent token totals
            → SearchClientFactory#fetchAgenticGlobalTopicHierarchy     — account-wide top topics
            → ArgusPostureService#buildInsightCards(...)        — shapes the above into 5 cards
    → api/fetchArgusPostureInsightSummaries   (separate call, fired right after — real LLM calls)
        → ArgusPostureAction#fetchArgusPostureInsightSummaries
            → recomputes the same 5 cards (cheap, no LLM), then
            → ArgusPostureService#buildInsightCardSummaries(ctx, cards, forceRefresh)
                → one ArgusInsightCardNarrativeHandler call per card, cached via the existing
                  InsightNarrativeCacheDao/InsightNarrativeCache (cardId used as the cache's
                  insightId — no schema change needed, the pre-existing narrativeMarkdown field is
                  exactly a single summary string already)
```

**The two endpoints are deliberately separate, not one slower call.** Card data is pure
Mongo/ES aggregation and stays fast; the AI summary is a real LLM round-trip on a cache miss (a few
hundred ms to a few seconds) and must never block the cards themselves from rendering — same
"fast data first, summary loads after" split this page's drilldown AI summaries already use
(`PostureDrillNarrativeService`), just synchronous-on-request here instead of background+poll,
since a card summary is small and cheap enough not to need that machinery.

### The 5 cards (`ArgusPostureService#buildInsightCards`)

| Card id | Data source | What it shows |
|---|---|---|
| `RED_TEAM_BREAKDOWN` | `TestingRunIssuesDao#openIssueGroupsForDashboard` (already-aggregated `AgentFindingGroup` rows, index-backed on `{status, _id.apiInfoKey.apiCollectionId}`) | Total open issues, breakdown by severity, and the single `{agent, vulnType}` group with the highest count |
| `RED_TEAM_HOTSPOT` | same `AgentFindingGroup` rows, re-aggregated in Java (`computeRedTeamStats`) | The agent with the most open issues overall, and the most common vuln type account-wide |
| `GUARDRAIL_BREAKDOWN` | `InsightService#fetchArgusMaliciousEvents` → `InsightsThreatBackendAccess#violationEventsMinimal` (a capped raw-row fetch, `minimalFields=true`, `MAX_THREAT_FETCH_LIMIT=100000` — same shape `SecurityPostureAction`'s own `trendWindowEventsFuture` already uses) | Total guardrail/malicious events, top policies (`DashboardMaliciousEvent#getFilterId()` as the label — see below), top agents (`getApiCollectionId()`) |
| `GUARDRAIL_HOTSPOT` | same event list, re-aggregated in Java (`computeGuardrailStats`) | The single hottest agent and single hottest policy |
| `OBSERVABILITY` | `SearchClientFactory#fetchAgenticServiceObservability` (per-agent token totals, live ES) + `SearchClientFactory#fetchAgenticGlobalTopicHierarchy` (account-wide top-5 topics/subtopics, live ES — new method, see below) | Total tokens used, the single hottest agent by tokens, top 5 topics with their subtopics |

**No server-side `{apiCollectionId, filterId}` aggregation exists on the threat-detection-backend**
for the guardrail cards — `MaliciousEventService` only supports `$match` + a few fixed `$group`
shapes (severity rank, risk score, dedupe), none grouped by policy+agent. `computeGuardrailStats`
aggregates the capped raw-row fetch in Java instead; this can undercount on an account with more
than 100,000 malicious events in the window, same accepted tradeoff `SecurityPostureAction` already
lives with for its own biggest-movers/sparkline panels. Flag a server-side aggregation endpoint as
a real follow-up if this ever needs to be exact.

**`DashboardMaliciousEvent#getFilterId()` is used as the policy label directly**, not joined to a
`GuardrailPolicies` document — the same convention `PostureService.criticalAlertsDrill` already
uses (`row("policy", e.getFilterId())`). A malicious event's `filterId` is not reliably a
`GuardrailPolicies._id` (see `MaliciousEventDto`'s own `contextSource`/legacy-filter-id handling in
`ThreatUtils`), so a name join would be fragile; the raw filterId string is what's shown instead.

**`fetchAgenticGlobalTopicHierarchy`** (`SearchClient`/`ElasticSearchClient`/`AzureDataExplorerClient`,
new) is the account-wide sibling of `fetchAgenticServiceObservability`'s per-service topic
hierarchy — same `AGG_TOPIC_HIERARCHY` terms-agg-of-terms-agg shape (`topic.keyword` → 
`subTopic.keyword`), just attached directly to the top-level ES aggs instead of nested under a
per-service `terms` agg. Returns `Map<String topic, Map<String subTopic, Integer count>>` — the
exact same shape `UserAnalysisData#topicHierarchy` already uses, so no new DTO was needed. Same
`atlasTrafficFilter=false` rationale as `fetchAgenticServiceObservability` (Argus/agentic traffic is
the "not Atlas" half of `AgentQueryRecord`'s `isAtlasTraffic` split), same ES-only-for-now/ADX-stub
precedent.

### AI summaries (`ArgusInsightCardNarrativeHandler`)

A much simpler sibling of `InsightNarrativeHandler`/`AbstractGroundedNarrativeHandler` than the old
per-finding rewrite handler this replaced: input is `{"facts": [{"key","label","formatted"}, ...]}`
— each card-builder method (`redTeamBreakdownCard`, etc.) appends its own `facts` array as it
builds the card's display fields, so the grounding the model sees is always exactly what the card
itself shows, with no separate fact-extraction step. Output is `{"summary": "<2-3 sentences>"}`.
Same retry-once + numeric-literal-guard machinery every `AbstractGroundedNarrativeHandler` subclass
shares — a summary containing a number not present in that card's own `facts` is rejected and
retried once, same as `InsightNarrativeHandler`.

Cached via the pre-existing `InsightNarrativeCacheDao`/`InsightNarrativeCache` with no schema
change: `cardId` (e.g. `"RED_TEAM_BREAKDOWN"`) is used as the cache row's `insightId` field even
though it isn't a real `InsightId` enum value — that field was always a loosely-typed cache key,
not a foreign key, so this is a safe reuse rather than a new cache mechanism.

### Deliberate simplifications

- **Owner** is still not shown anywhere on this page's Argus section — no data source was ever
  wired for it. Add it as a real field, never a placeholder, if/when one is.
- **The two endpoints recompute the same 5 cards' data independently** (`fetchArgusPostureInsights`
  and `fetchArgusPostureInsightSummaries` each call `ArgusPostureAction#buildCards`) rather than
  sharing a cache — deliberate: the card computation itself is cheap (Mongo aggregation + 2 ES
  calls, no LLM), and `InsightService#getOrLoadBundle`'s own 60s bundle cache already avoids
  redoing the `collections`/`policies` read specifically. Only the LLM call is expensive, and that
  half is cached separately via `InsightNarrativeCacheDao`.

### Drill-downs (`ArgusPostureService#fetchDrill`, `ArgusPostureAction#fetchArgusPostureDrill`)

Reuses `PostureDrillFlyout`/`PostureDrillResult`/`PostureDrillNarrativeService` wholesale, exactly
as this file's own "no drill-down for the Argus insight cards" open-work item said to — no second
flyout/DTO/narrative mechanism. `PostureDrillFlyout.jsx` gained one prop, `fetchDrill` (defaults to
`dashboardApi.fetchPostureDrill`), so `AgenticPosture.jsx` can pass
`postureDataSource.fetchInsightCardDrill` (→ `dashboardApi.fetchArgusPostureDrill` →
`api/fetchArgusPostureDrill`) instead, with the component itself unchanged otherwise. Same
`?drill=&path=` URL-sync convention `SecurityPosture.jsx` already uses.

Every Argus drill is **root-only** (one level, no member drill yet — see "Open work" below):

```
InsightCardsSection.jsx: card's own "View details" button → onOpenDrill(card.drillId)
    → AgenticPosture.jsx: ?drill=<id> (URL, {replace:true})
        → PostureDrillFlyout (fetchDrill=postureDataSource.fetchInsightCardDrill)
            → api/fetchArgusPostureDrill {drillId, path:"", skip, limit}
                → ArgusPostureAction#fetchArgusPostureDrill — fetches ONLY what this drillId needs
                  (openIssueGroups / maliciousEvents / serviceObservability — never all of
                  buildCards' own reads), same bundle-load-in-parallel pattern buildCards uses
                    → ArgusPostureService#fetchDrill(drillId, ...) dispatches to one of:
                        DRILL_RED_TEAM_ISSUES  → redTeamIssuesDrill  (openIssueGroups, sorted by
                          InsightUtil.severityRank then count desc)
                        DRILL_GUARDRAIL_EVENTS → guardrailEventsDrill (maliciousEvents, sorted by
                          severityRank then recency)
                        DRILL_OBSERVABILITY    → observabilityDrill  (serviceObservability, sorted
                          by total tokens desc — no severity concept, so severity stays null)
                      each caps its sorted row list at DRILL_ROW_CAP (50, "top 50 sorted worst-first"
                      — a fixed snapshot, not a true paginated total, same tradeoff
                      PostureService#criticalAlertsDrill already makes) via the shared
                      capAndPaginate, which also sets PostureDrillResult#severity from
                      PostureService.worstSeverity — then PostureService.paginate slices skip/limit
                      out of that capped list
                → PostureDrillNarrativeService.attachNarrative(result, ctx, drillId, path) — the
                  exact same InsightNarrativeHandler + InsightNarrativeCacheDao SecurityPostureAction
                  already uses, completely unmodified
            ← PostureDrillResult (title, breadcrumb, columns, rows, summary, ctas, severity,
              narrativeStatus/...)
```

`RED_TEAM_BREAKDOWN` and `ATTACK_FLOW_ANALYSIS` share `DRILL_RED_TEAM_ISSUES` (both are "the
account's open red-team issues", just summarized differently); `GUARDRAIL_BREAKDOWN` and
`GUARDRAIL_HOTSPOT` share `DRILL_GUARDRAIL_EVENTS` likewise — each card just sets its own
`drillId` field, computed by `ArgusPostureService`'s card builders, never guessed by the frontend.

**Severity badges** — `InsightCardsSection.jsx` renders `SeverityBadge` (the same component
`PostureDrillFlyout`/guardrail-violations tables already use) next to any card stat that carries a
real severity: a red-team card's top finding/issue (`AgentFindingGroup#getSecondary()`), plus the
account's full open-issue severity distribution (`RED_TEAM_BREAKDOWN.bySeverity`) as its own badge
row. Guardrail cards' top/hottest policy also gets a `severity` field now — resolved from the real
`GuardrailPolicies` document via the same `filterId == policy name` join `policyContextText`
already uses (`ArgusPostureService#attachPolicySeverity`) — null (no badge) when the policy name
doesn't resolve to a live document, never guessed. Observability rows have no severity concept at
all, so none was added there.

## Open work / where to pick this up

- **Monitoring coverage trend.** Still always-blurred dummy on the frontend
  (`isMonitoringCoverage` branch in `KpiTile`, `SecurityPosture.jsx`, is unconditional — doesn't
  check `hasRealSparkline`). Ask/decide before building: coverage today is device-based
  (`deviceIdToUsername` vs `applyToDeviceIds`) with **no historical snapshot** of either device
  liveness or policy targeting, so a *literal* historical coverage-% trend cannot be computed —
  only a proxy can. The user's direction was to build it "mostly on `ApiCollection.startTs`" — the
  live open question is what "covered" means at the collection/asset level (there is no
  ApiCollection→policy link field; the closest real mechanism is the host-scope fields on
  `GuardrailPolicies` — see the "`ApiCollection.startTs`" section above — which `PolicyHygieneProvider`
  /`GuardrailCoverageGapProvider` already evaluate per host). Whatever definition is chosen, follow
  the `shadowAiTrend` template (cumulative bucketing over `bundle.collections` by `startTs`), then
  gate the frontend's dummy overlay on `hasRealSparkline` the same way `KPI_CRITICAL_ALERTS`/
  `KPI_SENSITIVE_INCIDENTS` already are.
- **Framework readiness real data source** — item 8 of the plan, resolved: readiness is now
  clausesCovered/totalClauses, computed by `ComplianceClauseScanService` from real
  guardrail-violation traffic. Remaining follow-ups: (1) `ComplianceSubClauseCatalog`'s sub-clause
  lists are a first pass, not a legal/compliance-reviewed catalog — get them reviewed before
  treating the percentages as an audit claim; (2) `list_guardrail_violation_payloads` needs
  `apps/threat-detection-backend` redeployed before the scan can return anything — until then it
  degrades to the panel's gap state, not an error; (3) the scan is manually triggered (a button),
  not scheduled — consider a recurring job once the manual flow is validated.
- **Enforcement funnel's "warning overridden" bar** and **risk score drilldown trend** both need
  the same new thing: populate `AgentQueryRecord`'s already-declared-but-unfilled
  `guardrailAction`/`guardrailRule`/`guardrailSeverity` fields end-to-end (gateway → indexed field
  → `SearchClient` terms aggregation in **both** `ElasticSearchClient` and
  `AzureDataExplorerClient` — a one-sided addition regresses half of customers) — item 10 of the
  plan.
- **Vendor risk bubble chart** — blocked on a product decision (no vendor entity exists in Akto),
  not on engineering effort. Last item in the plan deliberately.
- **No dedicated test coverage yet for the 5 Argus insight cards** — `ArgusPostureService`'s
  `computeRedTeamStats`/`computeGuardrailStats`/card-builder methods are pure functions over
  plain `AgentFindingGroup`/`DashboardMaliciousEvent`/`UserAnalysisData` lists, so a
  `TestPostureService`-style hand-built-input unit test is straightforward to add — pick up there
  first if this area needs to change again.
- **Argus insight card drill-downs are built** (root level only — see "Argus insight card
  drilldowns" below for the how). Not yet done: a member level (e.g. a red-team issue group's own
  conversations, a guardrail policy's own individual devices) the way the 5 endpoint-posture panels
  have; and severity badges/CTAs weren't added to every card field, just the ones with a real
  resolvable severity (red-team issues, resolved guardrail policies) — vendor/observability rows
  have none to show.
- **Guardrail card accuracy is capped at `MAX_THREAT_FETCH_LIMIT` (100,000) events** — see "No
  server-side `{apiCollectionId, filterId}` aggregation exists" above. A real backend aggregation
  endpoint would remove this cap.
- Run `graphify update .` after modifying code in this area (per the repo-root `CLAUDE.md`) to
  keep the knowledge graph in sync — AST-only, no API cost.
