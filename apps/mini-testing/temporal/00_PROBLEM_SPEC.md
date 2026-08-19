# Spec 0 — Problem Statement, Scale & Goals

**Status:** Draft for review
**Scope:** Reliability + real-time progress for Akto test runs, orchestrated on **self-hosted Temporal**.
**Related:** [MVP spec](01_MVP_SPEC.md) · [Migration spec](02_MIGRATION_SPEC.md) · [Consumer internals](../CONSUMER_INTERNALS.md)

---

## 1. Context

A test run is a matrix of **M APIs × N tests** (N = test templates/subcategories). It's triggered from the UI and executed by a **mini-testing** worker.

Today, with `NEW_TESTING_ENABLED=true` / `RUNTIME_MODE=hybrid`:

- A **producer** fans the run out into one Kafka message per `(api, test)` cell.
- A **Confluent parallel consumer** + a fixed thread pool drain the topic and run each test.
- Persistence is **remote**: `dataActor → ClientActor → https://cyborg.akto.io → Akto-hosted Mongo`. There is no customer-local datastore.
- Workers run **inside the customer VPC** because the APIs under test are **not reachable from the public internet**. The worker reaches them locally and calls cyborg outbound for all data.

## 2. Problem statement

The current execution layer cannot reliably answer the three questions customers and our own engineers ask on every run:

| Question | Why it's unanswerable today |
|---|---|
| **Did the run complete?** | Completion is a heuristic: `workRemaining == 0` + a 5-min idle grace + a trusted-blindly `expectedRecords`. A restart, a mis-count, or a stalled queue all produce false "complete" or an indefinite hang. |
| **How many tests failed?** | The counter (`processedRecords`) increments in a `finally` block regardless of outcome — it conflates executed, skipped, rejected, and errored. Cells can be silently dropped (`RejectedExecutionException` / generic catch write no result). |
| **Why did it fail?** | Per-cell failure reasons are not durably captured against a per-cell identity. Timeouts can also produce **duplicate/conflicting results** (a timed-out row plus a late real row) because `future.cancel(true)` doesn't guarantee the test stops. |

Root cause: the system is built on **raw Kafka (at-least-once) + heuristic completion**, which pushes every durability, retry, dedup, and accounting concern up into application code — where it is currently incomplete. (Full analysis in [CONSUMER_INTERNALS.md](../CONSUMER_INTERNALS.md) §6–§7.)

Secondary gap: there is **no reliable real-time view** of a run's progress. Users cannot see the M×N matrix filling in, where a run is stuck, or a live failure count.

## 3. Scale requirements

| Dimension | Value |
|---|---|
| Tests (N) | ~1,000 templates |
| APIs per run (M) — **majority** | ~2,000 → **~2M cells/run** |
| APIs per run (M) — **max (largest customer)** | ~200,000 → **~200M cells/run** |
| Per-test timeout (current ceiling) | 5 min (safety cap; typical test ≪ 1s) |
| Tenancy | Multi-tenant; workers deployed per customer VPC (private-API reachability) |
| Run horizon | Minutes (small) to **many hours/days** (full 200M scan, rate-limit bound) |
| Deployment of orchestrator | **Self-hosted Temporal only** (see §6) |
| Persistence | Unchanged: results via cyborg → Akto-hosted Mongo |

Implication: a full-scale run spans hours/days and must survive worker crashes, deploys, and restarts without losing or duplicating work. Throughput is bounded by **target-API rate limits** and **fleet capacity**, not by orchestration.

## 4. Goals

### Functional
- **G1 — Deterministic completion.** "Complete" is a durable fact (workflow completion), not a heuristic. No false completes, no indefinite hangs.
- **G2 — Exact accounting.** Every cell reaches a terminal state (`PASSED | FAILED | ERRORED | TIMED_OUT | SKIPPED`) with a captured reason. Reported counts == reality, always.
- **G3 — Exactly-once effect.** Retries/restarts never duplicate results or issues (idempotent, per-cell keyed).
- **G4 — Real-time progress.** UI shows the M×N matrix filling live — run-level counters, per-shard progress, and per-cell drill-down — **read from the running orchestrator's memory, not from the datastore**.
- **G5 — Adaptive topology.** One code path scales from M=10 to M=200k; the run auto-designs its sharding/batching from actual `(M, N)`.

### Non-functional
- **G6 — Fairness / no starvation.** All active APIs progress concurrently under a global concurrency cap; no API monopolizes the budget.
- **G7 — Target-API safety.** Bounded per-API concurrency + global rate limiting; a scan never becomes a load test on a customer endpoint.
- **G8 — VPC-compatible.** Workers stay in the VPC; orchestrator reached via a single **outbound** connection. No new inbound exposure, no customer-run orchestration infra beyond the worker.
- **G9 — Flat, predictable cost.** Self-hosted orchestration cost independent of per-run/per-action metering.
- **G10 — Non-disruptive migration.** Execution engine (`TestExecutor`) and data path (cyborg) reused, not rewritten; roll out behind a flag with a shadow/parity phase.

## 5. Non-goals

- Rewriting `TestExecutor` or the test-editor/templating layer.
- Changing the persistence contract (cyborg → Mongo stays).
- Temporal Cloud (explicitly out — self-hosted only).
- Real-time streaming of all 200M cells to the browser (progress is aggregated/hierarchical; per-cell only for the drilled-in window).
- Fully air-gapped (zero-outbound) customers — they cannot use the hosted control plane today and are out of scope.

## 6. Constraints & assumptions

- **Self-hosted Temporal.** We run and operate the Temporal cluster (server + Postgres/Cassandra) on the Akto control plane, next to cyborg. Customers run **only** the mini-testing worker, which connects **outbound** to our Temporal frontend — exactly like it already connects to cyborg. Customers never run Temporal.
- Persistence stays remote via `ClientActor`/cyborg; the design must tolerate its latency by **bulk-writing** results.
- The private APIs are reachable **only** from inside the VPC worker; all test execution happens there.
- Config used for deterministic decisions (e.g. topology planning) is **pinned at run start** so workflow replay is stable.

## 7. Success metrics

| Metric | Target |
|---|---|
| Completion correctness | 100% of runs end in a definitive terminal state; 0 false completes; 0 indefinite hangs |
| Accounting drift | Reported terminal-state counts == actual, drift = 0 |
| Duplicate results | 0 duplicate/conflicting rows per cell |
| Crash recovery | 100% of cells reach terminal state after N induced worker crashes; 0 lost cells |
| Progress latency | < 1s from cell transition to UI |
| Topology | Same code correct at M ∈ {10; 2,000; 200,000} |
| Blast radius of migration | 0 changes to `TestExecutor`; 0 changes to persistence contract |
