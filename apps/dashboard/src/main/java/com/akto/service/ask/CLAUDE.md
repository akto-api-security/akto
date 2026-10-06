# Ask Akto overlay

A prompt-first command-palette overlay available on every page — one input that answers with
charts/graphs like the full chat page, plus a handful of always-on "worth doing now" tiles backed
by live numbers. Not a new page: no route, no redirect, no per-user layout preference. Opened by
the topbar's Ask Akto button (`Headers.js`, gated on `DASHBOARD_INSIGHTS`) or `⌘K`.

**There is no `domain` parameter anywhere.** Which dashboard's tiles, prompts and chat scope apply
is the current dashboard category, which `request.js` already sends on every call as
`x-context-source` (→ `Context.contextSource`). Request bodies carry nothing about it. A chat
question that belongs to another dashboard gets that context source's own "not my domain, go to
X" answer — the AI never re-routes a question to a different domain.

Two things it deliberately reuses rather than rebuilds: the agent (MCP `/generic_chat`, 35+
tools) and the insights engine (`InsightService`, provider registry, 60s-cached bundle). Read
`apps/dashboard/src/main/java/com/akto/service/insights/CLAUDE.md` if one exists for that engine's
own internals — this doc covers the overlay layered on top of it.

## How it works

```
Frontend (pages/dashboard/ask/)
    Headers.js topbar → AskOverlayButton (⌘K)
        → AskOverlay (Polaris Portal + Box sheet; useAskChat keeps the conversation across close/reopen)
            → HomeView      — typed text resolves locally (resolveCommand): "Ask Akto: <text>", go-to pages, suggested prompts
            → ChatView      — sendQuery(..., "COMMAND_PALETTE") → api/chatAndStore → AgentClient → MCP /generic_chat
            → tiles         — useAskData: api/fetchAskOverlay first (render), then api/fetchAskOverlayCuration (reorder + reword)
        category for copy/prompts: useDashboardCategory() (PersistStore.dashboardCategory) — never sent in a body

Backend
    AskOverlayAction#fetchAskOverlay (api/fetchAskOverlay, featureLabel=ASK_GPT/READ)
        → contextSource = Context.contextSource (x-context-source header; null → API)
        → Layer 1: RecommendationCatalog.compute(contextSource)      — cheap, no cache, every request
        → Layer 2: InsightService.buildAskOverlay(ctx, ...)          — CRITICAL/HIGH insight tiles, 60s-cached bundle
        → on-the-fly "what changed" feed (no ActivitiesDao — that collection is stale/dead)
    AskOverlayAction#fetchAskOverlayCuration (api/fetchAskOverlayCuration, same gate)
        → recomputes the same tiles server-side (never trusts client-sent tiles)
        → AskTileCurationService → AskTileCurationHandler (LLM): picks 1-4 tiles + the question each fires
        → cached in insight_narrative_cache keyed by md5(account | contextSource | version | tile content), 1-day TTL

MCP (two sibling repos, see below)
    ask-akto-mcp        — tool definitions + executors (read + two-phase write)
    agentic-testing      — chat service: conversationType/contextSource prompt + tool gating
```

## File map

### This repo (`akto`)

| File | Role |
|---|---|
| `service/ask/RecommendationCatalog.java` | Layer 1 — domain-dispatched cheap tiles, see below |
| `service/ask/Recommendation.java` | One tile: id/label/count/severity/prompt/route/params |
| `action/AskOverlayAction.java` | `api/fetchAskOverlay` + `api/fetchAskOverlayCuration` — context source from the header, optional `groups`, calls both layers |
| `service/ask/AskTileCurationService.java` + `AskTileCuration.java` | AI curation over the computed tiles — candidate filtering (zero-count recs excluded, <2 candidates → `SKIPPED`), cache, and dropping picks whose id isn't a real tile |
| `libs/utils/.../gpt_prompts/AskTileCurationHandler.java` | The LLM call. Same grounded-narrative base as the Argus cards: any number in a written prompt must be a tile's own value, or the response is rejected (one retry) |
| `service/insights/AskOverlayResponse.java` | `{recommendations, insightTiles, whatChanged, omittedGroups, generatedAt}` |
| `service/insights/InsightTile.java` | Narrower type than `InsightResult` — evidence/markdown/narrativeInput are structurally unreachable, not just nulled |
| `service/insights/InsightService.java` (`buildAskOverlay`) | Orchestrates both layers, per-group RBAC (`groupVisible`), CRITICAL/HIGH filter, 2-per-group cap |
| `service/insights/InsightId.java` (`Group`) | `API_POSTURE`, `TESTING_POSTURE` (added for this feature) + pre-existing `ATLAS_DISCOVERY`, `GUARDRAIL_VIOLATIONS` |
| `service/insights/InsightRoutes.java` | CTA route constants — every one a real `App.js` path; `THREAT_ACTIVITY` added for this feature |
| `service/insights/InsightDataBundle.java` | The 60s-cached per-account bundle; API_POSTURE/TESTING_POSTURE lazy accessors live here |
| `service/insights/InsightLazySources.java` | The actual lazy-read implementations bundle delegates to (own file — see "InsightLazySources lives on its own" below) |
| `service/insights/providers/{Unauthenticated​SensitiveApisProvider, UntestedHighRiskApisProvider, SensitiveDataHotspotsProvider, AgingOpenCriticalsProvider, IssueConcentrationProvider, IssueRecurrenceProvider}.java` | The 6 new API_POSTURE/TESTING_POSTURE insight providers |
| `web/.../pages/dashboard/ask/**` | Frontend — native Polaris only; colors with no stock token are `--p-color-*-ask-*` tokens in `askOverlay.css` |
| `web/.../components/layouts/header/Headers.js` | Mounts `<AskOverlayButton />` once, in the topbar, for every page |

### `ask-akto-mcp` (`/Users/aryankhandelwal/akto-code/clone-test/test-editor-services`, branch `feature/add_tools_write`)

Tool definitions/executors. No transport changes needed — every tool here stays inside an
existing name prefix (`akto_api_`, `akto_dashboard_`) so `getToolsByCategory` picks it up for
free.

| File | Added |
|---|---|
| `src/tools/api-security.ts` | 7 zero-Java read tools (`akto_api_posture_stats`, `akto_api_test_coverage`, `akto_api_issues_trend`, `akto_api_unauth_sensitive_count`, `akto_api_issues_fetch_results`, `akto_api_sensitive_locations`) + 4 two-phase write tools (`akto_api_issues_mark_false_positive`, `akto_api_issues_update_status`, `akto_api_issues_update_severity`, `akto_api_testing_start_run`) + `akto_api_endpoint_posture_summary` (wraps `api/fetchPostureSummary`, ENDPOINT-context summarization) |
| `src/tools/insights.ts` | Fixed a hardcoded id typo (`SKILL_EVALUATION_CONCENTRATION`→`EVALUATION_CONCENTRATION`); dropped the hand-duplicated `enum` constraint on `insightId` in favor of "call `akto_insights_list` first" |
| `src/tools/shared.ts` | `akto_dashboard_resolve_route` (pure lookup table, no dashboard call, closed intent enum — never a model-generated path); **`akto_dashboard_ask_overlay` was explicitly NOT built** — recommendations are dashboard-only, never AI-callable (user's explicit decision) |
| `src/types.ts` | `normalizeContextSourceHeader` — Java sends uppercase enum names (`AGENTIC`), the old validator required exact case |
| `src/index.ts` | Calls `normalizeContextSourceHeader` at the one place the header is read |

### `agentic-testing` (`/Users/aryankhandelwal/akto-code/test-editor-services`, branch `feature/agentic_testing`)

The chat service. `COMMAND_PALETTE` is a new `ConversationType`.

| File | Change |
|---|---|
| `src/utils/types.ts` | `+DOCS_AGENT, +INSIGHTS, +COMMAND_PALETTE` on `ConversationType` |
| `src/utils/utils.ts` | `COMMAND_PALETTE_PROMPT` (chart-capable, "overview first, depth on follow-up", two-phase write discipline); `MUTATING_TOOLS` split into `API_DOMAIN_WRITE_TOOLS` (API/DAST) vs `GUARDRAIL_POLICY_CREATE_TOOL` (Agentic/Endpoint); `allowToolsForContextSource` now allows `akto_insights_*` for API/DAST (was blocking it, silently dead-ending the insights routing already wired into those prompts) |
| `src/services/baseAgentService.ts` | `resolveSystemPrompt` appends `COMMAND_PALETTE`'s type-specific prompt even when `contextSource` is set (the overlay's normal case) — the write-discipline rules would otherwise be shadowed by contextSource's own prompt |
| `src/services/{anthropic,vertex}AgentService.ts` | `canUseTool`/`fetchTools` precedence is back to the original `contextSource ?? conversationType` — an earlier COMMAND_PALETTE-specific carve-out was reverted (see "Errors avoided" below) |

## Domain scoping — `CONTEXT_SOURCE`, not a separate enum

`RecommendationCatalog.compute(CONTEXT_SOURCE contextSource)` picks one of 3 tile sets, where
`contextSource` is the request's own `x-context-source`. This reuses
`com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE` (`API, MCP, GEN_AI, AGENTIC, DAST, ENDPOINT`)
rather than a parallel dashboard-only enum — `MCP`/`GEN_AI` fold into the same tile set as
`AGENTIC`. `AskOverlayAction.defaultGroupsFor` uses the same enum to pick the default
`InsightId.Group` set when the frontend doesn't send `groups` explicitly. The frontend's
suggested prompts are grouped the same way (`commandRegistry.suggestedPrompts(category)`):

| `contextSource` | Recommendation tiles | Default insight groups |
|---|---|---|
| `API` (also `DAST`, null/unrecognized) | open criticals, unauthenticated+sensitive, never tested, sensitive data types in responses | `API_POSTURE`, `TESTING_POSTURE` |
| `AGENTIC` (also `MCP`, `GEN_AI`) | red-teaming criticals, malicious MCP tools, unapproved MCP servers, threat activity (30d) | `ATLAS_DISCOVERY`, `GUARDRAIL_VIOLATIONS` |
| `ENDPOINT` | malicious skills in use, tokens used, active guardrail policies | `GUARDRAIL_VIOLATIONS` |

Every domain-specific recommendation query is additionally scoped to
`UsersCollectionsList.getContextCollectionsForUser(accountId, contextSource)` — an existing,
RBAC'd, cached utility (also used by the MCP-tool RBAC path) — so e.g. the API dashboard's "never
tested" count never includes agentic/MCP collections and vice versa. This was **not** true in an
earlier version of this file; retrofitted after review.

## Correctness traps already hit (don't re-discover these)

- **`findAll(Bson q)` (1-arg) is NOT RBAC'd.** Only `AccountsContextDaoWithRbac`'s 5-arg
  `findAll(q, skip, limit, sort, projection)` override calls `addRbacFilter`. `count(Bson)` IS
  overridden and RBAC'd. Every row-fetch in this feature uses the 5-arg form or `count()`
  deliberately — `AgenticObserveAction.getOrBuildSkillData()` (a file this feature reads from but
  doesn't own) uses the 1-arg form and was **not** fixed as part of this work; don't copy its
  pattern into new code here.
- **`TestingRunIssuesDao.getSeveritiesMapForCollections()` is a known-weak RBAC path** — it
  aggregates via raw `getMCollection()` with only a manual, exception-swallowed
  `UsersCollectionsList` filter (no admin/dashboardContext handling). Every open-issue-severity
  query in this feature uses `addCollectionsFilterForDashboard` instead (same filter
  `IssuesAction.fetchAllIssues` uses). `ApiCollectionsAction#fetchSeverityInfoInCollections` still
  calls the weak version — out of scope to fix here, flagged not fixed.
- **`SingleTypeInfoDao.generateFilterForSubtypes`'s `inResponseOnly` parameter is dead code** —
  the method always ORs in the request side regardless of that flag. Don't use it for a
  response-only query; `responseSensitiveSubtypeApiCounts` (added for this feature) builds its own
  response-only filter instead.
- **`SingleTypeInfo.count` is not a reliable hit counter** — `APICatalogSync` clamps its increment
  to 1 per sync cycle, so summing it is closer to "sync windows this param appeared in" than real
  traffic. `responseSensitiveSubtypeApiCounts` counts distinct **APIs** exposing a subtype instead
  (mirrors `CustomDataTypeAction#getCountOfApiVsDataType`'s established pattern), and every
  consumer (`RecommendationCatalog`, `InsightLazySources`, `SensitiveDataHotspotsProvider`) is
  labeled "APIs", never "hits"/"occurrences".
- **`addCollectionsFilterForDashboard` silently no-ops when `Context.userId`/`accountId` is
  unset** — fine for HTTP requests (`UserDetailsFilter` sets them), a real gap for any background
  job or MCP path that doesn't run inside a request.
- **Index gaps found and fixed**: `TestingRunIssuesDao` had no `{status, severity}` compound index
  (every open-criticals count fell back to a `{status}`-index scan); `ApiInfoDao` had no
  `{isSensitive}` index at all. Both added via `createIndexIfAbsent` (idempotent, additive). The
  `allAuthTypesFound` exact-array-match half of the unauthenticated+sensitive filter is still
  unindexed — flagged, not fixed (multikey-array compound indexing needs more care than a one-line
  addition).
- **Projections matter more than they look** — `ApiInfo` and `ApiCollection` are the fattest
  collections in the schema; every row-fetch in `RecommendationCatalog` projects to only the
  fields it reads (`tagsList`/`hostName` for MCP collection checks, `tagsList` alone for the
  skills scan, the two token fields for the usage sum).
- **`maliciousSkillsTotal()` has no cache**, unlike `AgenticObserveAction.getOrBuildSkillData()`
  (2-minute TTL) which it mirrors the query shape of. The underlying regex (`"skills/"` /
  `"/config/"`, unanchored) isn't index-backed either way — this is the one recommendation still
  worth caching if it shows up slow in practice.
- **`InsightLazySources` lives in its own file**, not nested inside `InsightDataLoader` — it was
  originally a `static final class LazySources` nested in the loader, but `InsightDataBundle` (the
  data holder) depended on `InsightDataLoader`'s inner type, an inverted/awkward direction. Moved
  to `InsightLazySources.java`, package-private, constructed once per `load()` call and handed to
  the bundle.

## Write-tool contract (two-phase, from the MCP layer)

Every write tool (`akto_api_issues_mark_false_positive`, `_update_status`, `_update_severity`,
`akto_api_testing_start_run`) takes `confirm` (default `false`):

- `confirm` absent/false → dry run: resolve targets, return what *would* change, write nothing,
  `requiresConfirmation: true`.
- `confirm: true` → the real write, **then re-read and report the actual count** —
  `bulkUpdateIssueStatus` returns SUCCESS even when zero documents matched, so a naive
  "wrote successfully" response would let the model announce a fix that never happened.

The overlay renders a dry-run response as an action chip; only the user's click sends
`confirm: true`. The system prompt (`COMMAND_PALETTE_PROMPT` in `agentic-testing`) explicitly
forbids the model from self-confirming.

## Errors made and reversed mid-build (context for why some code looks the way it does)

- Initially scoped `COMMAND_PALETTE_PROMPT` to be "terse, no charts" and added a tool-blocking
  carve-out in `canUseTool` for that conversation type — both directly contradicted the actual
  requirement (chart-capable answers, AI-triggerable writes from the palette). Reverted; the
  overlay's chat mode gets the same depth as the full chat page.
- `RecommendationDomain` (a new 3-value enum: API/AGENTIC/ENDPOINT) was built first, then replaced
  by reusing `CONTEXT_SOURCE` once it became clear the codebase already had the right enum plus a
  cached, RBAC'd "collections for this domain" resolver (`getContextCollectionsForUser`) that a
  parallel enum couldn't plug into.
- `guardrailsOverview()` originally counted *all* `GuardrailPolicies` rows on the (wrong)
  assumption that the DTO had no active/enabled flag — it does (`private boolean active`, Lombok
  `@Getter` on the class generates `isActive()`, easy to miss on a literal text grep). Fixed to
  filter `active=true`.

## Known gaps / deliberate simplifications

- `redTeamingCriticals()` (Agentic domain) uses the same CRITICAL-severity-issues query as the API
  domain's `openCriticals()`, only reworded — not narrowed to agentic-tagged collections beyond
  the standard `CONTEXT_SOURCE` scoping. Revisit if it reads as too broad in practice.
- `RecommendationCatalog`'s Endpoint tiles (`maliciousSkillsTotal`/`tokensUsedTotal`/
  `guardrailsOverview`) are independent cheap queries — they do **not** reuse
  `SecurityPostureAction`/`PostureService`'s richer, already-computed posture numbers (KPIs,
  enforcement funnel, framework readiness). The AI-answering path *does* reuse that page via
  `akto_api_endpoint_posture_summary`; the dashboard tiles don't. Unifying them is a real option,
  not yet done (`PostureService.buildSummary` needs the full bundle + several futures, so it isn't
  a drop-in cheap-tile replacement).
- No `RecommendationCatalog`-level cache — every field is either a genuinely cheap indexed count,
  or (see `maliciousSkillsTotal`) flagged as a caching candidate.
- Part 6 (RBAC/testing test suite) was scoped as design-level only — no automated tests were
  written for this feature. If adding them: unit-test `contextCollectionIds`/`groupVisible`
  first (their failure mode is a silently empty or over-broad overlay), then the resolver
  (`resolveCommand.js`, already pure/headless).
- No `AGENTIC_ASSETS`-page-equivalent recipient for a "recent prompts" list in the palette — the
  frontend uses a static `SUGGESTED_PROMPTS` array instead (see `palette/paletteHelpers.js`).

## Verification status

Every backend change in this feature was verified with **targeted `javac`** against the real
`.m2`/`target/classes` classpath (target/classes dirs first, `~/.m2` jars after — a stale shaded
jar in `.m2` otherwise shadows current `dao`/`dto` classes), never a full `mvn -pl apps/dashboard
-am compile` — that reactor build hangs in this environment on an unrelated protobuf/buf codegen
step. Frontend changes were verified with ESLint and manual `.d.ts` checks against the Polaris
version in use, not a running dev server. **No runtime/browser testing has been done on this
feature.** Before shipping: run the real `mvn` build in an environment where it doesn't hang, and
manually exercise `⌘K` → tiles → chat → a write-tool confirm round trip in a browser.
