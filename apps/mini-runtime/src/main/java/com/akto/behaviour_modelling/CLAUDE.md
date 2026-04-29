# Session Sequence Analyzer Module

## Purpose

This module tracks sequences of API calls made by users within sliding time windows (default: 10 minutes). It identifies patterns in user behavior by recording:
- **Raw API call counts** (how many times each endpoint was hit)
- **Transition counts** (how many times endpoint A was followed by endpoint B)
- **Probability scores** (what fraction of calls to A are followed by calls to B)

**Primary use case:** Detect anomalous users who deviate from expected API call sequences. For example:
- A user calls `/login` → `/dashboard` → `/settings` (normal)
- A user calls `/login` → `/admin` → `/export-data` (suspicious, hasn't authenticated for admin access)

---

## Architecture Overview

### Hot Path (Request Processing)
```
HttpResponseParams (from request)
         ↓
SessionAnalyzer.process()
         ↓
[1] Templatize URL → normalize /users/123 to /users/INTEGER
[2] Check if API is in catalog (known APIs only)
[3] Extract userId (default: source IP)
[4] Get or create UserSessionState for this user
[5] If deque is full, emit transition + record individual API
[6] Add to per-user deque (FIFO, max size = sequenceLength - 1)
         ↓
WindowAccumulator (LongAdder-backed, thread-safe)
```

### Window Lifecycle
```
Window N (0-10 min)              Window N+1 (10-20 min)
├─ Requests accumulate          ├─ Requests accumulate
├─ Per-user deques build        ├─ Per-user deques reset
└─ Transitions tallied          │  (lazy, on first request)
                                └─ New transitions begin
         ↓
[Scheduled] onWindowEnd() at 10-minute boundary
         ↓
[1] Swap to fresh accumulator (volatile, lock-free)
[2] Snapshot completed window
[3] Async flush to MongoDB
```

### Flush Pipeline
```
WindowSnapshot (immutable counts)
         ↓
ApiSequencesFlusher.flush()
         ↓
[1] For each transition (e.g., [/login, /dashboard]):
    - Verify both endpoints were hit this window
    - Record transitionCount, prevStateCount, lastStateCount
    - Compute precedenceScore = lastStateCount / total transitions to last
    - Emit ApiSequences DTO
[2] Cap at top 1000 by transitionCount (noise filter)
[3] Batch insert/upsert to MongoDB
```

### Database Persistence
```
MongoDB Collection: api_sequences
Filter (upsert): { apiCollectionId, paths }
Update: $add counts (cumulative), $divide probability from post-increment

Example Document:
{
  _id: ObjectId,
  apiCollectionId: 1001,
  paths: ["/users/INTEGER#GET", "/users/INTEGER#POST"],
  transitionCount: 150,      // cumulative across all windows
  prevStateCount: 200,       // total times /users/INTEGER#GET seen
  lastStateCount: 180,       // total times /users/INTEGER#POST seen
  probability: 0.75,         // computed in DB: 150/200
  precedenceScore: 0.833,    // computed in DB: 150/180
  lastUpdatedAt: 1704067200,
  createdAt: 1703990400,
  isActive: true
}
```

---

## Key Classes and Their Roles

### Core Orchestration

**`SessionAnalyzer`** (hot path orchestrator)
- Maintains volatile `currentAccumulator` (swapped atomically at window boundary)
- Per-user state map: `ConcurrentHashMap<String, UserSessionState>`
- Scheduled window-flip task (10 min default)
- Entry point: `process(HttpResponseParams record)`

**`SequenceAnalyzerConfig`** (dependency injection)
- `sequenceLength`: Max order (default 2 = bigrams, i.e., A→B pairs)
- `windowDurationMs`: Tumbling window size (default 10*60*1000 ms)
- `UserIdentifier`: Strategy to extract userId from request (default: IP-based)
- `AccumulatorFactory`: Fresh accumulator per window (default: RawCountAccumulator)
- `WindowFlusher`: Strategy to handle completed windows (default: ApiSequencesFlusher)
- `AktoPolicyNew`: Reference to API catalog for known-API guard

### Per-User State

**`UserSessionState`**
- `sessionStart`: Timestamp when this user's session began in current window
- `recentApis`: `Deque<ApiInfoKey>` — FIFO queue of recent API calls (max size = sequenceLength - 1)
- Lazy reset: If sessionStart < windowStart, deque is cleared on next request

### Counting and Accumulation

**`WindowAccumulator` (interface)**
- `recordApiCall(ApiInfoKey, userId)`: Increment count for individual endpoint
- `recordTransition(TransitionKey, userId)`: Record sequence of endpoints
- `snapshot(windowStart, windowEnd)`: Return immutable WindowSnapshot
- `reset()`: Clear state (used between windows)

**`RawCountAccumulator` (default implementation)**
- `ConcurrentHashMap<ApiInfoKey, LongAdder>` for API counts
- `ConcurrentHashMap<TransitionKey, LongAdder>` for transition counts
- `userId` parameter wired for phase-2 unique-user weighting (not yet used)

### Sequence Representation

**`TransitionKey`**
- Wraps `ApiInfoKey[] sequence` (e.g., 2 elements for bigrams, 3 for trigrams)
- Implements `equals()` and `hashCode()` using Arrays utilities
- Used as key in transitionCounts map

**`ApiInfoKey`**
- Templatized API endpoint: `"/users/INTEGER#POST"` (collection ID + method + URL)
- URL is already templatized by `AktoPolicyNew.generateFromHttpResponseParams()`
- Example: `/products/123` → `/products/INTEGER` (via parameterization rules)

### Window Management

**`WindowSnapshot`** (immutable)
- `windowStart`, `windowEnd`: Timestamps
- `apiCounts`: `Map<ApiInfoKey, Long>` — hits per endpoint this window
- `transitionCounts`: `Map<TransitionKey, Long>` — transitions per sequence this window
- `isEmpty()`: True if no transitions recorded

### Flushing and Persistence

**`WindowFlusher` (interface)**
- `flush(WindowSnapshot)`: Handle completed window asynchronously

**`ApiSequencesFlusher` (default implementation)**
- Converts snapshot → List<ApiSequences>
- For each transition: verify both endpoints were hit, compute counts and precedenceScore
- Sort by transitionCount, keep top 1000
- Call `DataActor.writeApiSequences()` (batched MongoDB upsert)

**`ApiSequences` (DTO)**
- Mirrors MongoDB document schema
- Constructor: `(apiCollectionId, paths, transitionCount, prevStateCount, lastStateCount, precedenceScore, probability)`
- Precedence and probability are computed in the database post-increment (sent as 0f from flusher)

---

## Critical Design Decisions

### 1. URL Templatization (Known API Guard)
**Why:** Prevent the model from being polluted by random/malicious URLs.

**How:** 
- `AktoPolicyNew.generateFromHttpResponseParams()` templatizes the raw URL (e.g., `/users/123` → `/users/INTEGER`)
- Catalog lookup checks if this templatized URL exists in `apiInfoCatalogMap`
- Unknown URLs are silently dropped (no error, just ignored)

**For anomaly detection:**
- Transitions only emit for known endpoints
- If an attacker calls `/users/123` before endpoint is known, it won't appear in sequences
- Once endpoint is cataloged, future calls are included

### 2. Per-User Deque (Lazy Reset)
**Why:** Track recent API calls per user without global sweep overhead.

**How:**
- Each user has a deque capped at `sequenceLength - 1` (default: 1 = just the prior call)
- On each request, check if `sessionStart < windowStart` (current window started after user's session)
- If true, reset deque; else, append new API
- Deque is synchronized per-user but not globally locked

**For anomaly detection:**
- Each user's recent history is independent
- Users idle for 10+ minutes have their deque reset naturally
- No background cleanup needed

### 3. Window Flip with Volatile Accumulator Swap
**Why:** Atomic window boundary without locking the hot path.

**How:**
- `currentAccumulator` is volatile (ensures all threads see new reference immediately)
- At window boundary: snapshot old accumulator, swap to fresh one, flush asynchronously
- Incoming requests never block on window swap

**For anomaly detection:**
- Windows are strictly 10 minutes (no overlap)
- Probabilities/counts are isolated per window then aggregated in DB
- Real-time visibility: can query DB for rolling-window probabilities (e.g., last 3 windows)

### 4. MongoDB Upsert on (apiCollectionId, paths)
**Why:** Accumulate transitions across multiple mini-runtime instances; avoid ID collisions.

**How:**
- Upsert filter: `{ apiCollectionId, paths }`
- Update: `$inc transitionCount`, `$set createdAt` (on insert only)
- Aggregation pipeline: two $set stages to compute probability from post-increment counts
- No explicit `_id` field; MongoDB auto-generates ObjectId

**For anomaly detection:**
- Probabilities are cumulative across all windows
- If you want rolling-window probabilities (e.g., last 1 hour), add a `window_id` field to the document
- Current schema does not track *when* each transition was seen (only cumulative count)

### 5. Top-1000 Cap Before Flush
**Why:** Prevent database bloat and filter noise.

**How:**
- Flusher sorts transitions by transitionCount descending
- Only writes top 1000 per window
- Least frequent transitions are discarded

**For anomaly detection:**
- Tail transitions (count < 10) may not appear in DB
- Use a lower threshold for training; adjust based on expected noise in your API traffic

---

## Data Flow Example: 2-User Scenario

### Setup
- Catalog: `/login` (POST), `/dashboard` (GET), `/settings` (GET)
- Window: 0-10 minutes
- Config: sequenceLength=2 (bigrams), IpBasedIdentifier

### Timeline
```
t=1s:  User A (IP=192.168.1.1) calls POST /login
       → sessionStart[A] = t=1s
       → recentApis[A] = []
       → recordApiCall(/login#POST)
       → no transition (deque < 1)

t=2s:  User B (IP=192.168.1.2) calls GET /dashboard
       → sessionStart[B] = t=2s
       → recentApis[B] = []
       → recordApiCall(/dashboard#GET)
       → no transition (deque < 1)

t=3s:  User A calls GET /dashboard
       → recentApis[A] = [/login#POST]
       → recordTransition([/login#POST, /dashboard#GET])
       → recordApiCall(/dashboard#GET)
       → recentApis[A] = [/dashboard#GET] (deque capped, /login removed)

t=4s:  User B calls GET /settings
       → recentApis[B] = [/dashboard#GET]
       → recordTransition([/dashboard#GET, /settings#GET])
       → recordApiCall(/settings#GET)
       → recentApis[B] = [/settings#GET]

t=600s (10 min): Window boundary
       → snapshot = {
           apiCounts: { /login#POST: 1, /dashboard#GET: 2, /settings#GET: 1 },
           transitionCounts: { [/login→/dashboard]: 1, [/dashboard→/settings]: 1 }
         }
       → flush to MongoDB:
           { apiCollectionId, paths: [/login, /dashboard], transitionCount: 1, prevStateCount: 1, lastStateCount: 2 }
           { apiCollectionId, paths: [/dashboard, /settings], transitionCount: 1, prevStateCount: 2, lastStateCount: 1 }
```

---

## Querying for Anomaly Detection

### Get All Sequences for a Collection
```javascript
db.api_sequences.find({ apiCollectionId: 1001, isActive: true }).sort({ probability: -1 })
```

### Get Expected Transitions (Probability > 50%)
```javascript
db.api_sequences.find({ apiCollectionId: 1001, probability: { $gte: 0.5 } })
```

### Get Transitions from a Specific Endpoint
```javascript
// Find all sequences starting with /users/INTEGER#GET
db.api_sequences.find({ apiCollectionId: 1001, paths: { $elemMatch: { $eq: "/users/INTEGER#GET", $position: 0 } } })
```

### Aggregate Probabilities by First Endpoint
```javascript
db.api_sequences.aggregate([
  { $match: { apiCollectionId: 1001 } },
  { $group: {
      _id: { $arrayElemAt: ["$paths", 0] },
      avgProbability: { $avg: "$probability" },
      count: { $sum: 1 }
    }
  },
  { $sort: { avgProbability: -1 } }
])
```

---

## Known Limitations

1. **URL Templatization Dependency:** If an endpoint URL is unknown when first requested, it won't appear in sequences until it's cataloged. Add new endpoints proactively to avoid gaps.

2. **Cumulative Counts Only:** No fine-grained temporal data. If you need to know *when* a transition became rare, you'll need to extend the schema with window timestamps.

3. **No Backward Compatibility:** Changing `sequenceLength` resets all deques (different sequence structure). Plan ahead if you need to switch between bigrams/trigrams.

4. **Noise from Early Windows:** First few windows may have incomplete data (users still warming up). Consider disabling anomaly detection for first 30 minutes of runtime.

5. **Source IP as userId:** Shared IPs (offices, proxies) are treated as one user; mobile users with changing IPs create multiple sessions.

6. **No Real-Time Updates:** Sequences are flushed every 10 minutes.

---

## Configuration Example

```java
SequenceAnalyzerConfig config = new SequenceAnalyzerConfig()
    .setSequenceLength(2)                                  // bigrams (A→B)
    .setWindowDurationMs(10 * 60 * 1000)                   // 10 minutes
    .setUserIdentifier(new IpBasedIdentifier())            // default: source IP
    .setAccumulatorFactory(() -> new RawCountAccumulator()) // default: raw counts
    .setFlusher(new ApiSequencesFlusher())                 // default: flush to DB
    .setAktoPolicyNew(aktoPolicyNew);                      // API catalog reference

SessionAnalyzer analyzer = new SessionAnalyzer(config);

// In request handler (e.g., HttpCallParser.handleResponseParams):
analyzer.process(httpResponseParams);

// On shutdown:
analyzer.shutdown(); // flushes any remaining data in current window
```

---

## Testing the Module

See `SessionAnalyzerTest.java` for behavioral tests covering:
- URL templatization and unknown API filtering
- Transition aggregation across users
- Window boundary isolation
- Multiple collection isolation
- Rapid user scale-up
- API method separation
- Per-user deque independence
