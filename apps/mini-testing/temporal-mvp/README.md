# mini-testing Temporal MVP (Java 17)

Runnable starting point for orchestrating Akto test runs on **self-hosted Temporal**.
Everything is named in your domain: **customers, APIs, tests** (no `M`/`N`/`cell` jargon).
Package `com.akto.testing.temporal` so it drops into `apps/mini-testing`. Specs:
[problem](../../apps/mini-testing/temporal/00_PROBLEM_SPEC.md) ·
[MVP](../../apps/mini-testing/temporal/01_MVP_SPEC.md) ·
[migration](../../apps/mini-testing/temporal/02_MIGRATION_SPEC.md).

## Vocabulary (code == your mental model)

| Concept | In the code |
|---|---|
| Customer / tenant | `customerId` → Temporal task queue `customer-<id>` |
| Test run | `TestRunWorkflow` (workflowId = `testRunId`) |
| APIs under test | `numApis` |
| Tests per API | `testsPerApi` |
| Total individual tests | `totalTests` = `numApis × testsPerApi` |
| APIs one activity tests (scheduled/retried unit) | **batch** — `testApiBatch`, `apisPerBatch`, `numBatches` |
| Large slice of a run (scale-out) | **shard** — `ApiShardWorkflow`, `apisPerShard`, `numShards` |
| Concurrency per API | `concurrentTestsPerApi` |
| Outcomes | `TestOutcomes` {passed, failed, errored, timedOut, skipped} |
| Live progress | `RunProgress` {totalTests, testsCompleted, batchesInProgress} |

## What it demonstrates

- **Reliability** — kill the worker mid-run → Temporal reschedules; the run completes.
- **Accounting** — every test reaches a terminal outcome; `Σ == totalTests`, no drift.
- **Live progress** — `RunStarter` polls a **Query** (workflow memory), not the datastore.
- **Adaptive strategy** — `Planner` picks SINGLE_WORKFLOW / GROUPED / GROUPED_WINDOWED.

## Prereqs

- Java 17, Maven
- Self-hosted Temporal dev server: `temporal server start-dev` (UI at http://localhost:8233)

## Build & run

```bash
MAVEN_OPTS= mvn -q -DskipTests package     # builds target/mvp.jar (shaded)
./demo.sh                                   # planner + live run + crash recovery
```

Run pieces:

```bash
java -cp target/mvp.jar com.akto.testing.temporal.PlanDemo
CUSTOMER=acme CONCURRENT_API_GROUPS=6 java -cp target/mvp.jar com.akto.testing.temporal.WorkerMain
CUSTOMER=acme NUM_APIS=2000 TESTS_PER_API=1000 LATENCY_MS=1 \
  java -cp target/mvp.jar com.akto.testing.temporal.RunStarter
```

## Map to production

| MVP piece | Production |
|---|---|
| `ApiTestingActivitiesImpl.runOneTest` (stub) | `TestExecutor.runTestNew(...)` |
| `ResultStore` (file) | idempotent bulk upsert via `ClientActor` → cyborg → Mongo |
| `newServiceStubs(127.0.0.1:7233)` | VPC worker → outbound mTLS to self-hosted Temporal frontend |
| task queue `customer-<id>` | per-tenant routing (already domain-shaped) |
| stub per-test timeout | per-test-type timeout (default 30–60s, 5-min cap for slow types) |

## Files

- `TestRunRequest` / `ExecutionConfig` — inputs (public fields → clean Temporal UI JSON).
- `Planner` / `ExecutionPlan` — pure, deterministic strategy selection.
- `TestRunWorkflow(Impl)` — one workflow per run; `progress()` query = live state.
- `ApiShardWorkflow(Impl)` — child workflow per API-shard (large runs only).
- `ApiTestingActivities(Impl)` — `testApiBatch`: per-API bounded concurrency, per-test timeout, heartbeat, idempotent resume.
- `ResultStore` — idempotent, crash-durable outcomes.
- `WorkerMain` / `RunStarter` / `PlanDemo` — entrypoints.
