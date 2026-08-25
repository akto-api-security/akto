# mini-testing

Standalone worker that executes Akto security test runs. A test run is a matrix
of **M APIs × N tests** (M = APIs under test, N = test subcategories/templates).
The UI triggers a run; a specific mini-testing instance picks it up and executes
every `(api, test)` cell.

Entry point: [Main.java](src/main/java/com/akto/testing/Main.java).

## Which instance runs a test run

Each process names itself via the `MINI_TESTING_NAME` env var
([Main.java:78](src/main/java/com/akto/testing/Main.java#L78)). A `TestingRun`
can pin itself to one or more instances:

- `allowedMiniTestingServiceNames` (list, newer) or `miniTestingServiceName`
  (single string, legacy) — checked in the main loop
  ([Main.java:621-634](src/main/java/com/akto/testing/Main.java#L621-L634)).
- If the run's allowed names don't include this instance's name, it's skipped
  and the loop polls again. This is how "run on a specific instance" works.

## The main loop

[`runModule()`](src/main/java/com/akto/testing/Main.java#L482) runs forever:

1. Bootstraps: Prometheus metrics, utility server, account settings, rate-limit
   watcher, playground poller, access-matrix analyzer, custom-datatype refresh.
2. Polls Mongo (via `DataActor`) for a pending `TestingRunResultSummary` (TRRS)
   or `TestingRun` assigned to this instance.
3. Resolves config, handles rerun/retry/overage/out-of-scope edge cases, creates
   or reuses a TRRS.
4. Dispatches execution (see below).
5. `markTestAsCompleteAndRunFunctions(...)` finalizes the run, then loops.

There are two execution paths, gated by **`IS_NEW_TESTING_ENABLED`**
([Main.java:813-818](src/main/java/com/akto/testing/Main.java#L813-L818)):

```java
if (Constants.IS_NEW_TESTING_ENABLED) {
    testingProducer.initProducer(testingRun, summaryId, false, syncLimit); // fan out to Kafka
    testingConsumer.init(maxRunTime);                                       // drain & run
} else {
    testExecutor.init(testingRun, summaryId, syncLimit, false);             // legacy in-process
}
```

> **This instance runs with `NEW_TESTING_ENABLED=true`**, so the
> Kafka producer/consumer path is the one in use. The legacy `TestExecutor.init`
> path is not exercised.

`Constants.IS_NEW_TESTING_ENABLED` is read from the `NEW_TESTING_ENABLED` env var.
It also gates creating the local testing-state folder
([Main.java:506-509](src/main/java/com/akto/testing/Main.java#L506-L509)) and the
crash-recovery resume block
([Main.java:542-544](src/main/java/com/akto/testing/Main.java#L542-L544)).

## New testing path: producer → Kafka → consumer

### Producer (fan-out)

`testingProducer.initProducer(...)`
([Main.java:814](src/main/java/com/akto/testing/Main.java#L814),
[Producer.java](src/main/java/com/akto/testing/kafka_utils/Producer.java)):

- Reads the test-run config, resolves the API list and the set of test
  subcategories.
- Emits **one Kafka message per `(apiInfoKey, subcategory)` cell** to the
  `TEST_RESULTS_TOPIC_NAME` topic — this is the M×N fan-out.
- Records shared run state (config, sample messages, expected record count,
  summary id) so the consumer can pick it up. Shared state lives in
  `TestingConfigurations` (in-memory singleton) and `TestingStateStore`
  (on-disk `BasicDBObject`, used for crash recovery).

Each message payload is a `SingleTestPayload`:
`testingRunId`, `testingRunResultSummaryId`, `apiInfoKey`, `subcategory`,
`testLogs`, `accountId`
([ConsumerUtil.parseTestMessage](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L71-L80)).

### Consumer (parallel execution)

[`ConsumerUtil.init(maxRunTimeInSeconds)`](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L186)
drains the topic and runs each test:

- Uses **Confluent Parallel Consumer** (`ParallelStreamProcessor`) with
  `UNORDERED` ordering, `maxConcurrency = instance.getMaxConcurrentRequest()`,
  `PERIODIC_CONSUMER_SYNC` commit mode, `batchSize=1`
  ([ConsumerUtil.java:256-265](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L256-L265)).
- For each record, submits the work to a **fixed thread pool**
  (`Executors.newFixedThreadPool(maxConcurrentRequest)`) and waits on the
  `Future` with a per-test timeout of `maxRunTimeForTests` (5 min)
  ([ConsumerUtil.java:280-322](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L280-L322)).
  So there are two layers of parallelism: the parallel consumer + the thread pool.
- Actual test execution:
  [`runTestFromMessage`](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L82)
  → looks up the `TestConfig` and sample messages, calls
  `TestExecutor.runTestNew(...)`, then persists logs, inserts results, and
  raises issues via `insertResultsAndMakeIssues`.
- On timeout/interrupt, writes a `TEST_TIMED_OUT` result
  ([`createTimedOutResultFromMessage`](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L127)).
- Tracks tested APIs in `testedApisMap` and bulk-updates their `lastTested`
  field at the end (`flushLastTestedUpdates`).

### The drain / completion loop

The `while (parallelConsumer != null)` loop
([ConsumerUtil.java:329-398](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L329-L398))
decides when a run is done. It stops when any of:

- The run is marked stopped (`GetRunningTestsStatus...isTestRunning` is false).
- `maxRunTime` is exceeded.
- All expected records are processed (`processed >= expectedRecords` and
  `workRemaining == 0`).
- **Idle grace**: queue empty with no progress for `DRAIN_IDLE_GRACE_MS` (5 min)
  → either restart the consumer from the last commit (if records are still
  missing) or complete.

Metric `AllMetrics.instance.setTestingKafkaQueuePending(workRemaining)` exposes
backlog.

### Crash recovery

Because Kafka commits are periodic (not per-message), a restart can reprocess
some records. On startup, `checkIfAlreadyTestIsRunningOnMachine()`
([Main.java:326](src/main/java/com/akto/testing/Main.java#L326)) reads
`TestingStateStore`; if a run was in flight for this instance, it re-runs the
producer + consumer to finish it
([Main.java:548-581](src/main/java/com/akto/testing/Main.java#L548-L581)).

On completion/failure the consumer's `finally` block flushes updates, shuts down
the pool, closes the consumer (drain-first on success, don't-drain on failure),
deletes the results topic, and clears `TestingStateStore`
([ConsumerUtil.java:409-442](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L409-L442)).

## Playground (separate, always-on path)

Independent of test runs, `checkForPlaygroundTest`
([Main.java:99](src/main/java/com/akto/testing/Main.java#L99)) polls every 2s for
interactive one-off requests from the test editor UI: test-editor playground,
Postman imports, login-flow tests, recorded JSON flows. These run inline (not via
Kafka) and write results straight back to the `TestingRunPlayground` document.

## Key env vars

| Var | Purpose |
|---|---|
| `NEW_TESTING_ENABLED` | `true` → Kafka producer/consumer path (this instance). |
| `MINI_TESTING_NAME` | Instance identity used to route test runs. |
| `KAFKA_BROKER_URL` | Kafka broker for the test-results topic. |
| `AKTO_MONGO_CONN` | Mongo connection (`DataActor` source of truth). |
| `DATABASE_ABSTRACTOR_SERVICE_TOKEN` | Auth for the data-abstractor service (hybrid/SaaS). |
| `RUNTIME_MODE` | e.g. `hybrid` — deployment mode. |
| `SKIP_SSRF_CHECK` | Skip SSRF guard (non-SaaS). |

## Key collaborators

- `DataActor` (`DataActorFactory.fetchInstance()`) — all Mongo I/O.
- `TestExecutor` — the engine that actually runs a single test against an API.
- `TestingConfigurations` — in-memory singleton holding the current run's config,
  test-config map, sample messages, concurrency.
- `TestingStateStore` — on-disk state for crash recovery.
- `Producer` / `ConsumerUtil` — Kafka fan-out and parallel execution.
- `TestCompletion` — finalizes a run (issues, summaries, notifications).
