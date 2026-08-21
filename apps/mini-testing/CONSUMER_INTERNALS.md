# ConsumerUtil internals: Kafka message handling, parallel execution & threadpools

A deep-dive into
[`ConsumerUtil.java`](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java)
— how a test run's Kafka messages are drained and executed, the two-layer
concurrency model, how the run decides it's "done", and where the design is
fragile.

Audience: engineers working on mini-testing. Read
[CLAUDE.md](CLAUDE.md) first for the module-level picture.

---

## 1. What ConsumerUtil is responsible for

The producer has already fanned a test run out into **one Kafka message per
`(api, test)` cell** on the `TEST_RESULTS_TOPIC_NAME` topic. `ConsumerUtil.init()`
is the other half: drain that topic, run each test, write results, and detect
when the whole run is finished (or should be abandoned).

Each message deserializes into a `SingleTestPayload`:

```
{ testingRunId, testingRunResultSummaryId, apiInfoKey, subcategory, testLogs, accountId }
```

---

## 2. The two-layer concurrency model (the important part)

This is the single most important thing to understand. There are **two
independent thread systems stacked on top of each other**:

```
                          Kafka topic: TEST_RESULTS_TOPIC_NAME
                                        │
                                        ▼
        ┌───────────────────────────────────────────────────────────┐
        │  LAYER 1: Confluent ParallelStreamProcessor                 │
        │  (io.confluent.parallelconsumer)                            │
        │                                                             │
        │   • ordering    = UNORDERED                                 │
        │   • maxConcurrency = instance.getMaxConcurrentRequest()     │
        │   • commitMode  = PERIODIC_CONSUMER_SYNC   (~every 5s)      │
        │   • batchSize   = 1                                         │
        │                                                             │
        │   Runs a pool of worker threads. Each thread invokes the    │
        │   poll(record -> {...}) callback for ONE record at a time.  │
        └───────────────────────────────────────────────────────────┘
                                        │  each callback does:
                                        ▼
        ┌───────────────────────────────────────────────────────────┐
        │  LAYER 2: ExecutorService  (fixed thread pool)              │
        │  Executors.newFixedThreadPool(maxConcurrentRequest)         │
        │                                                             │
        │   Future<?> f = executor.submit(() -> runTestFromMessage);  │
        │   f.get(maxRunTimeForTests = 300s, SECONDS);  // BLOCKS     │
        └───────────────────────────────────────────────────────────┘
                                        │
                                        ▼
                        TestExecutor.runTestNew(...)  → results in Mongo
```

### Why two layers?

The parallel consumer already gives you concurrency, so why submit to a *second*
pool and block on a `Future`?

**Solely to enforce a per-test timeout with forced cancellation.** The parallel
consumer has no clean "kill this record after N seconds" primitive. So the
callback offloads the real work to `executor`, then does
`future.get(300s)`; on `TimeoutException` it `future.cancel(true)` and writes a
`TEST_TIMED_OUT` result
([ConsumerUtil.java:284-311](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L284-L311)).

### The cost of two layers

Both pools are sized `maxConcurrentRequest`. For every in-flight test you burn
**two** threads:

```
  PC worker thread ─────────► blocked in future.get(), doing nothing
  executor thread  ─────────► actually running the test
```

So `2 × maxConcurrentRequest` threads sustain `maxConcurrentRequest` concurrent
tests. The Layer-1 threads are pure overhead — parked waiting. See
[critique §6.1](#61-the-two-pool-blocking-pattern-is-wasteful).

> **Aside — why this does *not* break `max.poll.interval.ms`:** in a vanilla
> `KafkaConsumer` blocking a poll thread for 300s would trigger a consumer-group
> rebalance. The parallel consumer decouples polling/heartbeat (its own control
> thread) from processing (worker threads), so blocking a worker is safe. This is
> the main reason the parallel consumer is used at all.

---

## 3. Lifecycle of a single message

```
 record arrives
      │
      ▼
 polledRecords++                                     (counter, for observability)
      │
      ▼
 executor.isShutdown() ? ──yes──► skip (do nothing)  ◄── happens after stop/max-time
      │ no
      ▼
 future = executor.submit(runTestFromMessage)
 firstRecordRead = true
      │
      ▼
 future.get(300s) ──────────────┬── OK ──────────► result already persisted inside task
      │                         │
      │                         ├── TimeoutException ─► future.cancel(true)
      │                         │                       createTimedOutResultFromMessage()
      │                         │
      │                         ├── InterruptedException ─► cancel + timed-out result
      │                         │
      │                         ├── RejectedExecutionException ─► cancel, NO result written (!)
      │                         │
      │                         └── other Exception ─► cancel, log only, NO result written (!)
      ▼
 finally: processedRecords++                          (ALWAYS, even on skip/failure)
      ▼
 callback returns normally ──► PC commits offset (periodically)
```

### Inside `runTestFromMessage` ([:82](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L82))

1. Parse payload, set `Context.accountId`, set activity context for the summary.
2. Look up `TestConfig` for the subcategory and the **sample messages** for the
   `apiInfoKey` from the in-memory `TestingConfigurations` singleton.
3. If no sample messages → log + skip (no result inserted).
4. Else `TestExecutor.runTestNew(...)` with the **last** sample, then
   `persistTestLogsToDb` + `insertResultsAndMakeIssues`.
5. Record `testedApisMap[apiInfoKey] = now` (bulk-flushed to `lastTested` at the
   end via `flushLastTestedUpdates`).

> **Key invariant the code deliberately enforces:** the poll callback almost
> never throws. Every failure path (timeout, interrupt, rejection, arbitrary
> exception) is caught *inside* the callback. So Layer 1 always sees "success"
> and commits the offset. This intentionally trades **at-most-once retry** for
> **no poison-pill stalling** — a broken test never blocks the queue, but it also
> never gets retried by the consumer. `maxFailureHistory(3)` is therefore
> effectively dead config on the happy path.

---

## 4. The drain / completion loop

After `poll(...)` registers the callback (non-blocking), the main thread enters a
supervisory loop ([:329-398](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L329-L398))
that polls every 100ms and decides when to stop.

```
        ┌──────────────────────── every 100ms ────────────────────────┐
        │                                                              │
        ▼                                                              │
  test marked stopped?  ──yes──► executor.shutdownNow(); BREAK         │
        │ no                                                           │
        ▼                                                              │
  now - startTime >= maxRunTime? ──yes──► executor.shutdownNow(); BREAK│
        │ no                                                           │
        ▼                                                              │
  workRemaining = parallelConsumer.workRemaining()                     │
  publish metric: testingKafkaQueuePending                             │
        │                                                              │
        ▼                                                              │
  locallyEmpty = firstRecordRead && workRemaining == 0                 │
        │                                                              │
   ┌────┴─────────────────────────────────────────┐                   │
   │ locallyEmpty == true                          │ false ───────────┘ (reset idle timer)
   ▼                                               
  processed >= expectedRecords ?                   
   ├─ yes ──► "all expected processed"; drain executor briefly; BREAK  
   │                                                                   
   └─ no ──► start/continue idle timer                                 
             idle >= DRAIN_IDLE_GRACE_MS (5 min) ?                     
               ├─ records still missing ─► restartConsumer = true; BREAK (outer loop rebuilds consumer)
               └─ else                   ─► drain executor briefly; BREAK
```

### Stop conditions, in priority order

| # | Condition | Action |
|---|-----------|--------|
| 1 | `isTestRunning(summaryId)` false (user stopped) | `shutdownNow()`, break |
| 2 | `now - startTime >= effectiveMaxRunTime` | `shutdownNow()`, break |
| 3 | queue empty **and** `processed >= expectedRecords` | graceful drain, break |
| 4 | queue empty 5 min, records still missing | **restart consumer** from last commit |
| 5 | queue empty 5 min, `expectedRecords` unknown/met | graceful drain, break |

`expectedRecords` comes from `TestingStateStore` (written by the producer). If
it's `-1` (unknown), conditions 3/4 don't fire and the run relies on the idle
grace + max-time.

### The "restart consumer" branch

`restartConsumer = true` breaks the inner loop but the **outer**
`while (restartConsumer)` ([:236](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L236))
tears down the parallel consumer + `KafkaConsumer`, builds fresh ones, and
re-subscribes — resuming from the last periodic sync commit. This is the recovery
path for the documented edge case:

> Because commits are periodic (~5s) not per-message, a mid-run module restart
> reprocesses whatever was consumed-but-not-yet-committed. **At-least-once
> delivery.**

---

## 5. Shutdown & cleanup (`finally`, [:409-442](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L409-L442))

```
 flushLastTestedUpdates()                     // bulk lastTested write
 shutdownExecutorQuietly(failed ? 5s : 30s, force=failed)
 parallelConsumer.close{DrainFirst | DontDrainFirst}   // drain on success, dump on failure
 consumer.close()
 Producer.deleteTestResultsTopic()            // topic is per-run, deleted after
 TestingStateStore.clear()                    // clears crash-recovery state
```

Note the topic is **created and destroyed per run** — `deleteTestResultsTopic()`
runs unconditionally at the end.

---

## 6. Design critique

### 6.1 The two-pool blocking pattern is wasteful

Layer 1 already provides bounded concurrency; wrapping each record in a Layer-2
`submit + future.get()` doubles thread count (one PC thread parked per active
test) purely to get a cancellable timeout. Alternatives worth considering:

- Run the test directly on the PC worker thread and enforce the timeout with a
  single shared `ScheduledExecutorService` watchdog that interrupts the worker.
- Or drop Layer 1's concurrency to 1 and let Layer 2 be the real pool (loses PC's
  offset/rebalance machinery — probably not worth it).

As written, sizing intuition is misleading: setting `maxConcurrentRequest = 150`
spins up ~300 threads.

### 6.2 `future.cancel(true)` does not guarantee the test stops → duplicate results

`cancel(true)` only *interrupts* the worker thread. `runTestNew` does network
I/O; if it's blocked in a socket read that doesn't honor interrupts, the task
keeps running after the timeout. Meanwhile `createTimedOutResultFromMessage`
already wrote a `TEST_TIMED_OUT` row. When the "zombie" task finally finishes it
calls `insertResultsAndMakeIssues` too → **two results (and possibly two issues)
for the same `(api, test)` cell**, one timed-out and one real. Whether this
surfaces depends on whether the insert path upserts or appends — worth verifying.

### 6.3 Silent result loss on rejection / generic exception

`RejectedExecutionException` ([:300](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L300))
and the generic `catch` ([:306](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L306))
cancel the future and log, but write **no result**. Yet `processedRecords++`
still runs in `finally`. Consequence: the completion gate (`processed >=
expected`) can be satisfied while some cells have **no result row at all** — the
summary silently under-reports. Contrast with the timeout path, which at least
writes a failed result. These two branches should probably also call
`createTimedOutResultFromMessage` (or a "failed to execute" equivalent).

### 6.4 `processedRecords` conflates "executed", "skipped", and "failed"

It increments in `finally` regardless of outcome (real result, no-sample skip,
rejection, exception). So "`processed >= expected` ⇒ run complete" is a
*coarse* gate that can complete a run whose result set is incomplete. It's a
throughput counter masquerading as a correctness signal.

### 6.5 Completion depends on `expectedRecords` being correct

`expectedRecords` is produced elsewhere and trusted blindly:

- **Too high** (e.g. producer counted cells it later skipped): condition 3 never
  fires; the run waits the full 5-min idle grace, then either restarts the
  consumer (spinning until max-time) or completes late. Wasted wall-clock.
- **Too low**: the run can be declared complete while records are still queued.

There's no reconciliation between "messages actually produced" and
`expectedRecords`.

### 6.6 The restart branch can spin uselessly

When records are genuinely unrecoverable (committed but their result-write
failed, or never produced), restart-from-last-commit can't bring them back. After
restart, if nothing new is consumed, `firstRecordRead` was reset to `false`, so
`locallyEmpty` stays false and the idle-grace branch never re-fires — the loop
just spins on `Thread.sleep(100)` until `maxRunTime`. Bounded, but it burns the
entire remaining budget doing nothing instead of concluding.

### 6.7 Control-flow coupling: the consumer only runs if a flag says so

The whole consume-and-drain body lives inside `while (restartConsumer)` where
`restartConsumer` is initialized from
`TestingStateStore.CONSUMER_RUNNING` ([:222](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L222),
[:235](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L235)). If
that flag is `false` when `init()` is called, **the consumer does nothing and
returns** — no records are ever drained. This makes correctness depend on some
upstream writer (the producer) having set `CONSUMER_RUNNING=true` before
`init()`. Overloading one boolean to mean both "should I run at all?" and "should
I restart?" is subtle and easy to break. A dedicated "start" call plus a separate
"restart" signal would be clearer.

### 6.8 Shared mutable statics

`executor` and `consumer` are `static` fields reassigned inside `init()`. This is
safe *only* because the main loop is single-threaded and runs one test run at a
time. Any move toward concurrent runs in one process would corrupt state. The
initial `newFixedThreadPool(150)` at class-load
([:65](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L65)) is
immediately discarded and replaced in `init()` — dead allocation.

### 6.9 `maxRunTimeForTests` (300s) is a hard-coded per-test ceiling

Independent of the run's overall `maxRunTime`. A single slow test can hold a
thread pair for 5 minutes. There's no per-test-type override.

### 6.10 Debug logging writes to the DB

`debugLogToDb` is gated by `KAFKA_DEBUG_MODE` but routes through
`warnAndAddToDb` — i.e. every debug line is a DB write. Under `KAFKA_DEBUG_MODE`
with high throughput this adds meaningful load on the hot path (once per record,
plus periodic progress).

---

## 7. Edge cases to keep in mind

| Scenario | Behavior today | Risk |
|---|---|---|
| Module restart mid-run | Reprocesses uncommitted records (at-least-once) | Duplicate results/issues for reprocessed cells |
| Test hangs on non-interruptible I/O | Timed-out result written; zombie task may later write real result | Duplicate + conflicting results (§6.2) |
| Executor rejects / throws | Counted as processed, **no result** | Silent missing cell (§6.3) |
| `expectedRecords` too high | Full idle-grace wait, maybe consumer restart | Long delay before completion (§6.5/6.6) |
| `expectedRecords` = -1 | Gate 3/4 disabled | Relies entirely on idle-grace + max-time |
| No sample messages for an API | Skipped, counted, no result | Cell missing from summary (arguably correct) |
| User stops run | `shutdownNow()`; in-flight tasks interrupted | Interrupted tasks may leave partial state |
| `CONSUMER_RUNNING` flag false at init | Consumer body skipped entirely | Silent no-op run (§6.7) |
| Very high `maxConcurrentRequest` | ~2× threads created | Thread/memory pressure (§6.1) |
| Periodic commit lands after result write fails | Offset advances past a cell with no result | Lost cell, unrecoverable by restart |

---

## 8. TL;DR

- **Layer 1** (Confluent parallel consumer) = bounded concurrency + offset
  management; **Layer 2** (fixed thread pool) = per-test cancellable timeout.
  Together they cost ~2× threads.
- The callback swallows all errors so offsets always commit → **no retries, no
  poison pills**, but individual tests can be lost or duplicated.
- Completion is decided by a supervisory loop using `workRemaining == 0` +
  `processed >= expectedRecords` + a 5-minute idle grace, with a
  restart-from-last-commit recovery path.
- Delivery is **at-least-once**; the main correctness risks are **duplicate
  results** (§6.2) and **silently missing results** (§6.3), both amplified by the
  coarse `processedRecords` gate (§6.4).
