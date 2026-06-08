# tasks/

## MaliciousTrafficDetectorTask

Main Kafka consumer. Reads raw HTTP traffic from `KafkaTopic.TRAFFIC_LOGS`, evaluates each request against threat detection rules, and pushes alerts to `KafkaTopic.ThreatDetection.ALERTS`.

The Kafka polling loop is **single-threaded by design** — `requestParams` and `responseParams` are static reused objects, not thread-safe.

---

## Per-request flow: `processRecord(HttpResponseParam)`

```
1. buildHttpResponseParam()         proto → HttpResponseParams (static reuse)
2. getActorId()                     → IP string; drop record if null/empty
3. AccountConfigurationCache        → isHyperscanEnabled flag
4. filterCache.getFilters()         → YAML filter map (5-min TTL)
                                      if hyperscan: remove DEFAULT_THREAT_PROTECTION_FILTER_IDS
5. RawApi.buildFromMessageNew()     → only if filters non-empty or hyperscan enabled
6. httpCallParser.createApiCollectionId() → int apiCollectionId
7. threatDetector.findMatchingUrlTemplate() → URLTemplate (if apiDistributionEnabled)
                                              used for aggregation (e.g. /users/INTEGER vs /users/123)
8. apiCountWindowBasedThresholdNotifier.incrementApiHitcount()  → per-API hit counter
9. filterCache: isIgnoredEvent / isSuccessfulExploit checks
10. distributionCalculator.processRequest()  → async XADD to Redis stream (zero sync calls)
11. hyperscanEventHandler.detectAndPushEvents()  → if hyperscan enabled
12. filter loop (YAML):
      for each FilterConfig:
        - skip ipApiRateLimitFilter (handled by distributionCalculator)
        - SchemaConform: RequestValidator.validate() (gated to specific account IDs, partially disabled)
        - else: threatDetector.applyFilter()
        - if matched: threatDetector.shouldIgnoreApi() → skip if ignore matches
        - if not ignored and not aggregated: generateAndPushMaliciousEventRequest(SINGLE)
        - if aggregation rules:
            API_LEVEL_RATE_LIMITING → apiCountWindowBasedThresholdNotifier.calcApiCount()
            else                    → windowBasedThresholdNotifier.shouldNotify()
            if threshold breached   → generateAndPushMaliciousEventRequest(AGGREGATED)
```

---

## Alert emission: `generateAndPushMaliciousEventRequest()`

Builds a `MaliciousEventMessage` proto and sends it:

```
internalKafka.send(KafkaTopic.ThreatDetection.ALERTS, MaliciousEventKafkaEnvelope)
```

**Key fields in the message:**
- `filterId` — which rule matched
- `actor` — IP string
- `detectedAt` — request timestamp
- `eventType` — `SINGLE` or `AGGREGATED`
- `category` / `subCategory` / `severity` — from the filter config
- `latestApiEndpoint`, `latestApiMethod`, `latestApiPayload` — request snapshot
- `metadata` — arbitrary string (filter-specific context)
- `type` — hardcoded `"Rule-Based"` for YAML/hyperscan filters

`sessionId` is currently hardcoded to `""` — wired for future use.

---

## Key dependencies (constructor-injected)

| Field | Type | Purpose |
|-------|------|---------|
| `threatConfigEvaluator` | `ThreatConfigurationEvaluator` | Actor extraction, rate limit config, param enumeration config |
| `filterCache` | `FilterCache` | YAML filters, schema cache, ignore/exploit filter buckets |
| `windowBasedThresholdNotifier` | `WindowBasedThresholdNotifier` | Sliding-window aggregation for YAML rules (`RedisBackedCounterCache`, prefix `"wbt"`, window 10 min) |
| `apiCountWindowBasedThresholdNotifier` | `WindowBasedThresholdNotifier` | API-level rate limiting (uses `ApiCountCacheLayer`) |
| `distributionCalculator` | `DistributionCalculator` | Per-IP/API behavioral data → async Redis stream |
| `hyperscanEventHandler` | `HyperscanEventHandler` | High-performance regex detection, callback to `generateAndPushMaliciousEventRequest` |
| `internalKafka` | `KafkaProtoProducer` | Alert output Kafka |
| `httpCallParser` | `HttpCallParser` | `createApiCollectionId()` — maps request to collection |
| `rawApiFactory` | `RawApiMetadataFactory` | IP geolocation metadata (country code) |

---

## Static / hardcoded constants

- `DEFAULT_THREAT_PROTECTION_FILTER_IDS` — 8 filter IDs skipped when hyperscan is enabled: `LocalFileInclusionLFIRFI`, `NoSQLInjection`, `OSCommandInjection`, `SQLInjection`, `SSRF`, `SecurityMisconfig`, `WindowsCommandInjection`, `XSS`
- `ipApiRateLimitFilter` — loaded once via `Utils.getipApiRateLimitFilter()`, skipped in YAML loop (handled by `distributionCalculator`)
- `MAX_APPLY_FILTER_LOGS = 1000` — limits verbose logging for `applyFilter` calls

---

## Traffic ignored by `ignoreTrafficFilter()`

Records are dropped early if:
- Request header `x-akto-ignore` is present
- Path contains `/api/threat_detection`, `/api/dashboard`, or `/api/ingestData`
- `host` header matches `Constants.AKTO_THREAT_PROTECTION_BACKEND_HOST`

---

## Adding a new detection type

To inject a new behavioral signal (e.g., sequence-based anomaly):

1. Add a new field to the constructor (e.g., `SequenceAnomalyDetector`)
2. Inject after step 7 (after `urlForAggregation` is known — actor + templatized URL are both available)
3. If anomalous, call `generateAndPushMaliciousEventRequest()` with a synthetic `FilterConfig` carrying the new filter ID, category, and severity
4. `sessionId` field in `MaliciousEventMessage` is already wired — can be populated if needed

**What is available at injection point (after step 7):**
- `actor` — IP string
- `apiCollectionId` — int
- `urlForAggregation` — templatized URL string (e.g., `/users/INTEGER`)
- `method` — `URLMethods.Method`
- `responseParam.getTime()` — epoch seconds
- `metadata` — country code, dest country code
