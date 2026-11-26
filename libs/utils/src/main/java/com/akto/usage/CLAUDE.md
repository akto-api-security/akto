# Usage metering (billed metrics)

## Purpose
Every account is measured once an hour. The numbers become the org's usage in Stigg and gate ingestion in the runtimes.
A wrong number here is billed, so a metric that cannot be measured is skipped and logged, never reported as 0.

## Metrics (`MetricTypes`)
| Metric | Counts | Unit |
|---|---|---|
| `ACTIVE_ENDPOINTS` | endpoints in API and DAST collections: everything except agentic collections, endpoint-source collections, demos and deactivated | endpoints |
| `AI_ASSET_COUNT` | collections matching `UsersCollectionsList.agenticCollectionsFilter()` (MCP, GenAI or ai-agent-caller tag, not endpoint-source), minus demos, deactivated and the unauthenticated MCP tools group | collections |
| `ENDPOINT_ASSET_COUNT` | the largest `endpointsCount` (distinct devices) among "AI Agent" groups, the same figure the Agentic assets page shows per row | devices |
| `MCP_ASSET_COUNT` | retired: never computed, aggregated, reported or enforced. The enum value stays so stored documents still decode | n/a |

`TEST_RUNS`, `CUSTOM_TESTS` and `ACTIVE_ACCOUNTS` are unchanged. `MetricTypes.measured()` is the list the cron and
billing iterate; it excludes `MCP_ASSET_COUNT`.

## Data flow
`InitializerListener.setupUsageScheduler` (hourly, dibs lock) -> `calcUsage` -> `UsageMetricHandler.calcAndSyncAccountUsage`
-> `recordEachMetric` over `MetricTypes.measured()` -> `UsageMetricCalculator.calculateUsageMetric` -> insert `UsageMetric`
-> sync to the usage service -> billing `UsageCalculator.aggregateUsageForOrg` (latest per account and metric, summed)
-> `UsageCalculator.buildStiggReport` -> Stigg.

`ENDPOINT_ASSET_COUNT` runs `AgenticAssetGrouping.computeMaxEndpointsPerAiAgent`: a fresh read of every collection and
the same grouping the Agentic assets page uses (`AgenticAssetGrouping.classifyAllGroups`), with empty traffic and risk maps
because the device count does not depend on them. It bypasses the page's 60s classification cache.

Ingestion: `HttpCallParser` -> `APICatalogSync.syncWithDB` -> `SyncLimitPolicy.forCollection`:
- API and DAST collections: per-endpoint `SyncLimit` against `ACTIVE_ENDPOINTS`, deltas reported.
- Agentic collections: no per-endpoint limit and no deltas. Over the `AI_ASSET_COUNT` limit,
  `UsageMetricUtils.shouldSkipNewAsset` blocks only collections created after `overageFirstDetected`.
- Endpoint-source collections: not limited by the api-runtime.

## Rules
- A metric that cannot be measured throws; `recordEachMetric` catches per metric, writes no document, logs the failure
  and continues. Billing then sees the account's usage as missing, not 0.
- Billing skips one metric that has no Stigg feature id (or is unknown to the build) instead of dropping the whole org.
- `shouldSkipNewAsset`: entitlement missing blocks; unlimited or under the limit never blocks; an unknown overage time
  (`<= 0`) never blocks existing collections; a collection created exactly at the overage time is not new.
- `ApiCollection.isAgenticCollection()` (runtime, in memory) and `agenticCollectionsFilter()` (Mongo) must agree.
  `TestApiCollectionAgentic` compares them for every tag combination. The Mongo filter also lists the unauthenticated MCP
  tools group for the dashboard; usage and the runtime deliberately ignore it, since a group holds no endpoints.
- Asset metrics (`AI_ASSET_COUNT`, `ENDPOINT_ASSET_COUNT`) are written only by the hourly cron, never by endpoint deltas.

## Rollout
- Stigg first: create the `ENDPOINT_ASSET_COUNT` feature, set `StiggConfig.endpointAssetsLabel`, and re-set every plan's
  `AI_ASSET_COUNT` limit in collections. Set `MCP_ASSET_COUNT` to unlimited rather than deleting it: older runtimes treat a
  missing entitlement as limit 0.
- Deploy order: billing, cyborg and every dashboard first (they must decode the new enum value), then the dashboard that
  measures `ENDPOINT_ASSET_COUNT`. Ship the api-runtime with or before it: an old api-runtime still adds endpoint deltas to
  `AI_ASSET_COUNT`, which can push it over its limit between cron runs and block new collections.
- No kill switch for `ENDPOINT_ASSET_COUNT`; stopping it needs a redeploy. Enforcement is switched off by setting the Stigg
  limit to -1.

## Gotchas
- `AI_ASSET_COUNT` changed unit from endpoints to collections in place. Its Stigg history mixes both units.
- `ENDPOINT_ASSET_COUNT` is computed for every account, including ones with no agents: a full collection read plus
  grouping per account per hour. `AgenticObserveAction` records the collection read at 1-3s on a 26k-collection account;
  grouping time when uncontended is unverified.
- Accounts listed in `aggregateIncomplete` get 0 for a metric with no document. That is an existing per-org ops override.
- A skipped metric still marks that day's usage document as synced to Stigg. Fix the label and the next hourly aggregate
  re-reports it.
- JUnit 4 tests do not start under Maven in `libs/utils` and `apps/api-runtime` (the vintage engine cannot parse the junit
  version) and the build still passes with `Tests run: 0`. Write JUnit 5 there, or put the test in `apps/dashboard`
  or `apps/billing`. Ten tests in `TestDBSync` and `TestMergingNew` fail when run by hand.
- `APICatalogSync` needs a database at class load, so decision logic that must be unit tested lives in `SyncLimitPolicy`.

## Observability
- Cron: `measured <METRIC>: <n> for account <id> ... duration: <ms>` per metric and
  `Usage recorded for account <id>: measured=<a> failed=<b> [...]` per account. An error line means a metric was skipped.
- Billing: `Feature id not found for metric` or `Unknown metric type` per skipped metric, and a per-org skipped summary.
- api-runtime: `Limit reached for account <id>, skipped <n> new endpoints in collection <id>`.

## Open work
- Customer-hosted mini-runtime builds: apply `SyncLimitPolicy`'s gate to agentic collections and stop reporting endpoint
  deltas for them; cyborg `updateUsage` should ignore deltas for `AI_ASSET_COUNT` and `ENDPOINT_ASSET_COUNT` from older
  mini-runtimes.
- Skipping `ENDPOINT_ASSET_COUNT` for accounts with no agent-tagged collection needs a check that matches every way an
  "AI Agent" group can arise, including services typed through `ai-agent-caller`.

Tests: `TestUsageMetricCalculator`, `TestUsageMetricHandler`, `TestShouldSkipNewAsset` (apps/dashboard),
`TestUsageCalculatorStigg` (apps/billing), `TestSyncLimitPolicy` (apps/api-runtime), `TestApiCollectionAgentic` and
`TestMetricTypes` (libs/dao).
