# API Hit Count Flow

Tracks how many times each API endpoint is called per minute across all consumers. Purpose: detect API-level abuse (e.g., a flood of requests to one endpoint regardless of which IP is making them).

## Problem Statement

**Example**: In a 10-minute window, `/api/login` was called 50,000 times — across 200 different IPs, each under the per-IP rate limit. The IP-level rate limiter misses this. The API-level hit counter catches it.

---

## Architecture: Three Layers

### Layer 1: Hot Path – Request Ingestion (`DistributionCalculator`)

Per HTTP request, two extra fields are packed into the same Redis stream message already used by the distribution module.

**Called from**: [MaliciousTrafficDetectorTask.java](../tasks/MaliciousTrafficDetectorTask.java)

**Extra fields added to `threat_input_stream` body**:
```
apiLevelWindow     = sliding window size (minutes) from API_LEVEL_RATE_LIMITING filter rule
apiLevelThreshold  = match count from API_LEVEL_RATE_LIMITING filter rule
```

These values are fetched once per request via `FilterCache.getApiLevelRateLimitRule()` and passed into `DistributionCalculator.processRequest()`. If no rule is configured, both default to `0` (disables the check).

**Why pass through stream?** The background consumer has no access to `FilterCache`. Embedding the config in the message avoids cross-thread state sharing.

---

### Layer 2: Cold Path – Batch Processing (Lua Script in `DistributionStreamConsumer`)

The same `async_threat_process.lua` script that handles IP distribution also handles API hit counting. This runs atomically per batch in Redis.

#### Lua: Step 5 — API-Level Hit Count

For each message in the batch:

**1. Increment per-bin counter**:
```lua
local apiCountKey = "apiCount|" .. apiKey .. "|" .. currentMin
redis.call('INCR', apiCountKey)
redis.call('EXPIRE', apiCountKey, 28800)  -- 8 hour TTL
```
- Key format: `apiCount|{collectionId}|{url}|{method}|{binId}`
- `binId` = epoch seconds / 60 (i.e., the current minute)
- TTL is 8 hours — long enough to survive Kafka lag and relay cron

**2. Index the API for cron relay**:
```lua
redis.call('ZADD', "apiCountIndex", currentMin, apiKey)
```
- `apiCountIndex` is a Sorted Set: member = `{collectionId}|{url}|{method}`, score = `binId`
- Enables the relay cron to find all active APIs in a time range via `ZRANGEBYSCORE`

**3. Threshold check (if enabled)**:
```lua
if apiLevelWindow > 0 and apiLevelThreshold > 0 then
    local apiTotal = 0
    for i = currentMin - apiLevelWindow + 1, currentMin do
        local v = redis.call('GET', "apiCount|" .. apiKey .. "|" .. i)
        if v then apiTotal = apiTotal + tonumber(v) end
    end
    if apiTotal >= apiLevelThreshold then
        table.insert(apiCountBreaches, (m - 1) .. "|" .. apiTotal)
    end
end
```
- Sums counts across the sliding window (`apiLevelWindow` bins)
- If total ≥ threshold, adds to `apiCountBreaches` list
- Returned to Java alongside `ipRateBreaches`: `return {breaches, apiCountBreaches}`

**Why inside Lua?** All Redis reads/writes for a batch are atomic. No race between consumers — each message is processed exactly once via consumer group.

#### Java: `handleApiCountBreach()`

When the Lua script returns a non-empty `apiCountBreaches` list:

```
breachData = "messageIndex|totalCount"
```

1. Parse `messageIndex` → look up original stream message body
2. Extract `apiKey` = `{collectionId}|{url}|{method}`
3. Call `pushBreachEvent()` with `API_LEVEL_RATE_LIMIT_FILTER`
4. Push `MaliciousEventKafkaEnvelope` to `KafkaTopic.ThreatDetection.ALERTS`

The event uses `EventType.EVENT_TYPE_AGGREGATED` and filter sub-category `API_LEVEL_RATE_LIMITING`.

---

### Layer 3: Relay Cron (`ApiCountInfoRelayCron`)

Runs every 1 minute. Reads all per-bin counts from Redis and bulk-inserts them into the DB for dashboards and historical analysis.

**Window scanned**: `[now/60 - 8*60, now/60 - 5]`
- Lower bound: 8-hour lookback (handles Kafka lag)
- Upper bound: 5 minutes behind now (ensures in-flight bins have settled)

**Steps**:

1. **Find active APIs** via index:
   ```java
   cache.fetchApiTuplesFromIndex(startBinId, endBinId)
   // ZRANGEBYSCORE apiCountIndex startBinId endBinId
   // Returns: ["123|/api/users|GET", "456|/api/login|POST", ...]
   ```

2. **Fetch all per-bin counts** in a single MGET:
   ```java
   cache.fetchCountsForWindow(apiTuples, startBinId, endBinId)
   // Builds keys: ["apiCount|123|/api/users|GET|88320", "apiCount|123|/api/users|GET|88321", ...]
   // Single MGET returns all values
   ```

3. **Parse and build records**:
   - Key format: `apiCount|{collectionId}|{url}|{method}|{binId}`
   - Creates `ApiHitCountInfo(collectionId, url, method, count, binId)` per non-null entry

4. **Bulk insert to DB**:
   ```java
   dataActor.bulkInsertApiHitCount(toInsert)
   ```

5. **Cleanup index** (removes processed range):
   ```java
   cache.removeIndexRange(startBinId, endBinId)
   // ZREMRANGEBYSCORE apiCountIndex startBinId endBinId
   ```

**Safety**: If `apiTuples.size() > 10,000`, a warning is logged — indicates stream lag or cron missed a cycle.

---

## Redis Data Structures

| Key | Type | TTL | Written By | Read By |
|---|---|---|---|---|
| `apiCount\|{collectionId}\|{url}\|{method}\|{binId}` | String (integer) | 8h | Lua script | Relay cron (MGET) |
| `apiCountIndex` | Sorted Set | ∞ | Lua script (`ZADD score=binId`) | Relay cron (`ZRANGEBYSCORE`) |

---

## Filter / Threshold Configuration

Thresholds come from the `API_LEVEL_RATE_LIMITING` filter's `AggregationRules`:

```
FilterConfig (subCategory = "API_LEVEL_RATE_LIMITING")
  └── AggregationRules
        └── Rule[0]
              ├── condition.windowThreshold  → apiLevelWindow (minutes)
              └── condition.matchCount       → apiLevelThreshold (total requests)
```

Fetched via `FilterCache.getApiLevelRateLimitRule()` in the hot path, embedded in each stream message.

If no filter is configured, `apiLevelWindow = 0` and `apiLevelThreshold = 0` — the Lua script skips the threshold check entirely (but still increments counters and updates the index for the relay cron).

---

## Data Flow Example

**Scenario**: `/api/login` (collection 456) receives 1,000 requests/min across 50 IPs. The configured rule: window=5 min, threshold=3,000.

**Minute 88320**:
1. Hot path: 1,000 requests → 1,000 XADD to `threat_input_stream`, each with `apiLevelWindow=5`, `apiLevelThreshold=3000`

2. Cold path (2× batches of 500):
   - Lua: `apiCount|456|/api/login|POST|88320` → INCR'd 1,000 times
   - `apiCountIndex` has score-88320 entry for `456|/api/login|POST`
   - For each message: sum bins [88316..88320] → e.g., 2,800 total (below threshold, no breach yet)

**Minute 88321**: another 1,000 requests
   - `apiCount|456|/api/login|POST|88321` → INCR'd 1,000 times
   - Sum bins [88317..88321] → 2,000 (still below 3,000)

**Minute 88322**: another 1,000 requests
   - Sum bins [88318..88322] → 3,000 → **breach** → `apiCountBreaches` returned
   - `handleApiCountBreach()` fires → Kafka alert with `API_LEVEL_RATE_LIMITING` filter

**Relay cron (every minute)**:
   - `ZRANGEBYSCORE apiCountIndex 87840 88315` → finds `456|/api/login|POST`
   - MGET all `apiCount|456|/api/login|POST|{bin}` keys in range
   - Bulk-inserts `ApiHitCountInfo` records to DB
   - `ZREMRANGEBYSCORE` cleans up processed range

---

## Key Design Decisions

### Why reuse the existing stream instead of a separate one?
All the metadata needed (apiKey, minute, actor, host, accountId) is already in the `threat_input_stream` messages. Adding 2 fields is cheaper than a parallel stream.

### Why not check threshold in the hot path?
The hot path runs on every request. A synchronous Redis `GET` loop over `apiLevelWindow` keys (up to 30 keys) per request would add significant latency under load. The Lua script batches 500 messages and does this once.

### Why ZADD with score = binId instead of a per-bin set?
A single `apiCountIndex` sorted set lets the cron find all active APIs in any time range with one `ZRANGEBYSCORE` call. Per-bin sets would require iterating over every bin individually.

### Why not reset counter after breach?
Unlike IP mitigation (which sets a `ratelimit:...:mitigation` key to suppress re-alerts), API-level breaches are not suppressed. Each batch that still exceeds the threshold will fire another alert. This is intentional — the filter deduplication happens downstream in the alert pipeline.
