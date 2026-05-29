# threat-detection

Real-time API security module. Reads raw HTTP traffic from Kafka, applies threat detection policies, and pushes malicious event alerts back to Kafka for the backend to consume.

## How it works

```
Kafka (akto.api.logs2)
    → MaliciousTrafficDetectorTask          # main consumer loop
        → ThreatConfigurationEvaluator      # resolves actor ID + rate limit config
        → ThreatDetectorWithStrategy        # applies YAML filter rules
        → HyperscanEventHandler             # optional regex-based detection (fast path)
        → DistributionCalculator            # behavioral: per-IP/API counters via Redis streams
        → WindowBasedThresholdNotifier      # aggregation: sliding window threshold alerts
    → internalKafka (KafkaTopic.ThreatDetection.ALERTS)
        → SendMaliciousEventsToBackend      # forwards alerts to backend service
```

Entry point: `Main.java` — wires up Redis, Kafka configs, crons, and starts the tasks.

## Key submodules

| Package | Responsibility |
|---|---|
| `tasks/` | Core Kafka consumer workers and threat evaluation pipeline |
| `hyperscan/` | High-performance regex pattern matching (replaces YAML filters when enabled) |
| `ip_api_counter/` | Behavioral detection — param enumeration, IP/API hit distribution via Count-Min Sketch |
| `smart_event_detector/` | Sliding window threshold notifier for aggregated threat rules |
| `cache/` | Redis-backed caches for account config, filter rules, OpenAPI schemas, API counts, and counter state |
| `crons/` | Background schedulers (API count relay, distribution forwarding) |
| `strategy/` | Detection strategy abstractions (YAML filter vs. hyperscan) |
| `constants/` | Kafka topic names and Redis key patterns |

## Detection modes

Two modes, controlled per-account via `AccountConfigurationCache`:

- **YAML filter mode** (default): Rules loaded from `FilterYamlTemplateDao`, evaluated by `ThreatDetectorWithStrategy`. Supports aggregation rules and ignore conditions.
- **Hyperscan mode** (`isHyperscanEnabled`): Skips the 8 default YAML filter IDs (`SQLInjection`, `XSS`, `LFI`, etc.) and routes them through `HyperscanEventHandler` instead. Custom YAML templates still run.

## Important env vars

| Var | Purpose |
|---|---|
| `AKTO_TRAFFIC_KAFKA_BOOTSTRAP_SERVER` | Source traffic Kafka |
| `AKTO_INTERNAL_KAFKA_BOOTSTRAP_SERVER` | Internal alerts Kafka |
| `AKTO_THREAT_DETECTION_LOCAL_REDIS_URI` | Local Redis for counters/caching |
| `AGGREGATION_RULES_ENABLED` | Enables Redis + API count relay cron (default: true) |
| `API_DISTRIBUTION_ENABLED` | Enables per-IP/API behavioral tracking (default: true) |

## Actor identification

Actor = the IP/identity attributed to a request. Resolved by `ThreatConfigurationEvaluator.getActorId()`. Records with no actor are dropped before any detection runs.

## Filter lifecycle

Filters are fetched from DB every 5 minutes via `FilterCache.getFilters()`. On each refresh, filters are split into three buckets:
- `apiFilters` — active threat detection filters
- `successfulExploitFilters` — post-match severity escalation
- `ignoredEventFilters` — suppress alerts matching known-safe patterns

`FilterCache` also owns the OpenAPI schema Redis cache (`getApiSchema()`), with a 24-hour TTL and DB fallback when Redis is unavailable.

## Notes

- `buildHttpResponseParam()` in `MaliciousTrafficDetectorTask` reuses static `requestParams`/`responseParams` objects (not thread-safe — the Kafka polling loop is single-threaded by design).
- Schema conformance detection (`SchemaConform` category) is currently gated to specific account IDs and partially disabled — do not expand without checking `handleSchemaConformFilter()`.
- Each submodule has its own `CLAUDE.md` with deeper detail.
