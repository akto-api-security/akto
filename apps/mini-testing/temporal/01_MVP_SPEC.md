# Spec 1 — MVP Specification

**Status:** Draft for review
**Goal:** Cheaply prove the risky claims from [Spec 0](00_PROBLEM_SPEC.md) with a runnable demo, on **self-hosted Temporal**, without touching the execution engine or data path.
**Related:** [Problem spec](00_PROBLEM_SPEC.md) · [Migration spec](02_MIGRATION_SPEC.md)

---

## 1. Objective

The MVP is a **risk-reduction exercise**, not the product. It must demonstrate, with a working system:

| Claim | Maps to goal |
|---|---|
| C1 — A run survives worker crashes and completes exactly once | G1, G3 |
| C2 — Every cell has an exact terminal state + reason; counts never drift | G2 |
| C3 — Live M×N progress read from workflow memory (no datastore) | G4 |
| C4 — One code path adapts topology from M=10 to M=200k | G5 |

Non-goals for the MVP: production hardening, multi-tenant auth, real login flows, full cyborg wiring, Temporal HA cluster.

## 2. Scope

| In | Out |
|---|---|
| Self-hosted Temporal **dev server** (`temporal server start-dev`) | Production Temporal cluster / HA (see migration Phase 0) |
| `TestRunWorkflow` + adaptive planner (flat + sharded) | continue-as-new waves (only needed > ~4M APIs) |
| `runApiSuite` activity: bounded per-API parallelism (`K`), per-test timeout, heartbeat-resume | Rewriting `TestExecutor` |
| Idempotent result store (local, per-cell keyed) | Full `ClientActor`/cyborg integration |
| Stubbed execution (configurable latency + fail/timeout rates); optional 1 real collection | Real auth, rate-limit backends |
| Live progress via Query + minimal grid UI | Production UI |
| Fault injection harness (kill worker, inject failures) | Multi-tenant task-queue routing |

## 3. Architecture

```
  temporal server start-dev            ← self-hosted, local (Postgres-less dev mode)
        ▲ outbound gRPC (:7233)
        │
   ┌────┴─────────────┐     ┌──────────────────────────────┐
   │  Client / trigger │     │  Worker (poll task queue)     │
   │  starts a run     │     │   • TestRunWorkflow           │
   └───────────────────┘     │   • runApiSuite activity      │
        │ Query(progress)     │        └─ stubbed runTest     │
        ▼                     └──────────────────────────────┘
   Grid UI (reads workflow          │ idempotent upsert
   in-memory state, no DB)          ▼
                              local result store (per-cell keyed)
```

## 4. Components

### 4.1 Domain model

- **Cell** = `(apiIndex, testIndex)`, identity key `runId:apiIndex:testIndex`.
- **Cell state** = `PENDING | RUNNING | PASSED | FAILED | ERRORED | TIMED_OUT | SKIPPED`.
- **Run input** = `{ runId, M, N, capacity }`.

### 4.2 Adaptive planner (pure function — deterministic)

```
plan(M, N, cap):
  apisPerActivity = clamp(cap.targetCellsPerActivity / max(N,1), 1, cap.maxApisPerActivity)
  numActivities   = ceil(M / apisPerActivity)
  if numActivities <= cap.maxUnitsPerWorkflow:      return FLAT(apisPerActivity)
  shardSize = cap.maxUnitsPerWorkflow * apisPerActivity
  numShards = ceil(M / shardSize)
  if numShards <= cap.maxUnitsPerWorkflow:           return SHARDED(numShards, shardSize, apisPerActivity)
  return SHARDED_CAN(...)   // out of MVP scope, planner returns it but demo uses FLAT/SHARDED
```

Defaults: `targetCellsPerActivity=1000`, `maxUnitsPerWorkflow=2000`, `maxApisPerActivity=200`.
Result: M=10 → FLAT(1 activity, 10 APIs batched); M=2,000 → FLAT(2,000 activities); M=200,000 → SHARDED(100 shards).

### 4.3 `TestRunWorkflow`

- Computes `plan(M, N, capacity)` at start (capacity pinned in input for replay-safety).
- FLAT: schedules `runApiSuite` activities directly.
- SHARDED: spawns `ShardWorkflow` children, each schedules its activities.
- Maintains in-memory aggregate `{ total, pending, running, passed, failed, errored, timedOut, skipped }` and a per-shard summary.
- Exposes `@QueryMethod progress()` → the aggregate (drives the UI; no DB read).
- Completes when all activities/children complete → completion is the durable answer to "did it finish".

### 4.4 `runApiSuite` activity (per API)

- Input: `apiIndex`, `testIndex` list.
- Internal bounded pool `Semaphore(K)` runs tests concurrently (fairness: one API can't exceed `K`).
- Each test wrapped with `PER_TEST_TIMEOUT` (default 5s in MVP for demo speed; represents the 5-min prod cap) → on timeout record `TIMED_OUT` and continue.
- **Idempotent resume:** on retry, read already-persisted cells for this API and skip them.
- **Heartbeat** after each test (liveness + progress).
- Bulk-flush results to the store (proves the bulk-write contract).
- Returns `BatchResult { passed, failed, errored, timedOut }`.

### 4.5 Concurrency dials (orthogonal to topology)

| Dial | Meaning | MVP default |
|---|---|---|
| `K` | per-API concurrency | 8 |
| `S` | activities per worker (`maxConcurrentActivityExecutionSize`) | 20 |
| global | `S × workers × K` (or task-queue rate) | derived |

### 4.6 Result store

MVP uses a local key-value store (in-process map or SQLite) with idempotent upsert keyed by cell id. Mirrors the future cyborg contract: `upsertResult(cellKey, outcome, reason)` + `fetchPersistedCells(runId, apiIndex)`.

## 5. Live progress UI

- A small page polls `progress()` (or subscribes via SSE bridge) every ~500ms and renders:
  - Run-level progress bar + counters (passed/failed/errored/timedOut).
  - A canvas M×N heatmap (aggregated tiles for large M; per-cell for small M).
- Data comes from **workflow memory via Query**, demonstrating G4.

## 6. Demo scenarios (acceptance)

| # | Scenario | Expected |
|---|---|---|
| D1 | Start M=2,000 × N=1,000 run (stub) | Grid fills live; completes; counts sum to M×N |
| D2 | `kill -9` worker mid-run, restart | Run resumes from last committed state; no lost/duplicate cells; still completes |
| D3 | Inject 7% fail + 3% timeout | Final `failed`/`timedOut` counts match injected rates within stub tolerance |
| D4 | Re-run at M=10 and M=200,000 | Same code; planner logs FLAT then SHARDED; both complete |
| D5 | Duplicate delivery (re-run same cell) | Idempotent upsert → single row, no duplicate |

## 7. Success criteria (measurable)

- **Completion:** D1–D4 all reach a definitive terminal workflow state.
- **Recovery:** after ≥3 induced crashes (D2), 100% of cells terminal, 0 lost, 0 duplicate.
- **Accounting:** `passed+failed+errored+timedOut+skipped == M×N` for every run; injected-rate match (D3).
- **Progress latency:** UI reflects a cell transition in < 1s.
- **Isolation:** no dependency on `TestExecutor` internals or the real persistence layer (stubbed).

## 8. Test plan (MVP)

- **Unit:** `plan()` across M ∈ {1, 10, 2k, 200k, 4M}, N ∈ {1, 1k}; boundary assertions on activity/shard counts.
- **Workflow replay test:** Temporal replay test over a recorded history to prove determinism.
- **Integration:** end-to-end run against the dev server; assert final counts == M×N.
- **Chaos:** automated worker-kill mid-run; assert recovery invariants.
- **Idempotency:** double-execute a cell; assert single persisted row.

## 9. Effort

~2 engineer-weeks core (Weeks 1–2), +1 week to wire one real collection through `TestExecutor`. Timeline and week-by-week deliverables in the buy-in summary.

## 10. Language note

The showcase demo is built in TypeScript (fastest to run against self-hosted Temporal); the **production target is Java** (mini-testing). Temporal concepts (workflow, activity, heartbeat, query, child workflow, replay determinism) map 1:1 across SDKs — the migration (Spec 2) is specified in Java.
