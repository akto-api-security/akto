# Spec 2 — Migration Specification (phased)

**Status:** Draft for review
**Goal:** Move production test-run orchestration from the Kafka producer/consumer to **self-hosted Temporal**, incrementally, with each phase independently tested and reversible.
**Related:** [Problem spec](00_PROBLEM_SPEC.md) · [MVP spec](01_MVP_SPEC.md)

---

## Principles

1. **Strangler pattern.** The Temporal path grows alongside the Kafka path behind a flag; Kafka is retired only after parity is proven.
2. **Reuse, don't rewrite.** `TestExecutor` and the cyborg persistence contract are reused unchanged.
3. **Every phase ships behind a flag, is tested to an exit bar, and has a rollback.**
4. **Self-hosted Temporal only.** We operate the cluster on the control plane; VPC workers connect outbound.
5. **No phase may increase blast radius before parity is measured** (shadow before cutover).

Flag: `TESTING_ORCHESTRATOR = KAFKA | TEMPORAL | SHADOW` (per-account / per-run overridable).

---

## Phase 0 — Cluster foundations

**Goal:** A production-grade self-hosted Temporal cluster on the control plane.

**Work**
- Deploy Temporal (frontend/history/matching/worker services) + **Postgres** (or Cassandra at high scale) via Helm/k8s next to cyborg.
- TLS on the frontend; namespace per environment (`testing-dev`, `testing-prod`).
- Expose the frontend for **outbound** VPC-worker connections (mTLS); firewall = egress-only from VPC.
- Observability: Temporal metrics → existing Prometheus/Grafana; retention & archival policy for histories.
- Backup/restore runbook for the Temporal DB.

**Tests**
- Cluster health + smoke workflow (hello-world) passes.
- **Failover test:** kill a history/matching pod; in-flight smoke workflow continues.
- DB backup → restore drill; workflow state intact.
- VPC-worker connectivity test from a sandbox VPC (outbound-only, mTLS).

**Exit criteria:** smoke workflows survive pod failure; VPC sandbox worker connects and executes; backup/restore verified.
**Rollback:** none needed (no production traffic yet).

---

## Phase 1 — Workflow skeleton behind a flag (stubbed execution)

**Goal:** Land the Java `TestRunWorkflow` + planner in mini-testing, exercised with stubbed execution.

**Work**
- Add Temporal Java SDK to mini-testing; worker registers `TestRunWorkflow`, `ShardWorkflow`, `runApiSuite`.
- Implement the **adaptive planner** (flat / sharded / CAN) as a pure, unit-tested function.
- `runApiSuite` with bounded `K`, per-test timeout, heartbeat-resume — calling a **stubbed** `runTest` (no real API calls, no cyborg writes).
- Gate on `TESTING_ORCHESTRATOR=TEMPORAL`; default remains `KAFKA`.

**Tests**
- **Unit:** planner across M ∈ {1,10,2k,200k,4M} × N ∈ {1,1k}; assert activity/shard/CAN selection + counts.
- **Replay determinism:** Temporal `WorkflowReplayer` over recorded histories.
- **Integration:** end-to-end stubbed run on the dev/staging cluster; assert `Σ terminal == M×N`.
- **Chaos:** worker-kill mid-run; assert recovery invariants (0 lost, 0 duplicate).

**Exit criteria:** all four test suites green in CI; stubbed 2M-cell run completes on staging with correct counts and survives a kill.
**Rollback:** flag off (KAFKA path unaffected).

---

## Phase 2 — Real execution + idempotent persistence

**Goal:** Run **real** tests via `TestExecutor` and persist via cyborg, for a single opt-in collection.

**Work**
- Wire `runApiSuite` to `TestExecutor.runTestNew(...)` (reuse existing execution).
- Implement idempotent persistence through `ClientActor`/cyborg: bulk upsert keyed `(summaryId, apiInfoKey, subcategory)`; `fetchPersistedCells` for resume-skip.
- Map cell terminal states + reasons onto the existing `TestingRunResult` model.

**Tests**
- **Result parity:** run the same small collection through KAFKA and TEMPORAL; assert identical result set (per-cell outcome + issues) modulo timestamps.
- **Idempotency:** force activity retry (inject transient error); assert no duplicate rows/issues.
- **Timeout behavior:** inject a hung test; assert single `TIMED_OUT` row, no late duplicate (fixes the current double-result bug).
- **Persistence load:** measure cyborg write rate; assert bulk-write batching holds under a 100k-cell run.

**Exit criteria:** result parity == 100% on the pilot collection; 0 duplicates under forced retries; bulk-write within cyborg SLOs.
**Rollback:** flag off per-collection.

---

## Phase 3 — Shadow mode (parity at real volume)

**Goal:** Run the Temporal path **in parallel** with Kafka on a sample of real runs; compare, don't serve.

**Work**
- `TESTING_ORCHESTRATOR=SHADOW`: Kafka path remains authoritative (writes results); Temporal path runs the same run and writes to a **shadow namespace/collection**.
- Diff engine compares completion, counts, per-cell outcomes, durations; emits parity metrics + alerts on divergence.
- Sample ramp: 1% → 10% → 50% of eligible runs.

**Tests**
- **Parity dashboard:** divergence rate, missing/extra cells, outcome mismatches, completion-time delta.
- **Soak:** multi-hour shadow runs; assert no memory/history growth issues, no stuck workflows.
- **Crash injection in prod-like env:** kill VPC worker during shadow run; assert recovery + parity.

**Exit criteria:** ≥ 2 weeks at 50% shadow with divergence < agreed threshold (target ~0 outcome mismatches; completion parity within tolerance).
**Rollback:** SHADOW is non-authoritative — disable anytime with zero customer impact.

---

## Phase 4 — Cutover for opt-in tenants

**Goal:** Make Temporal **authoritative** for opt-in accounts (including self-hosted VPC workers).

**Work**
- Flip `TESTING_ORCHESTRATOR=TEMPORAL` per opt-in account.
- Per-tenant **task queue** routing (`tenant-<id>`); VPC worker polls its queue outbound.
- Live progress UI wired to Query/SSE for these tenants.
- Runbook + on-call dashboards (stuck workflows, retry storms, queue backlog).

**Tests**
- **Canary:** internal account first, then friendly customers.
- **VPC end-to-end:** real run from a customer VPC worker → central Temporal → cyborg; crash recovery verified in situ.
- **Load:** sustained real runs at the tenant's normal cadence; assert SLOs (completion correctness, progress latency < 1s).
- **Fallback drill:** flip a tenant back to KAFKA mid-incident; verify clean handover.

**Exit criteria:** canary tenants stable ≥ 2 weeks; 0 correctness regressions vs their Kafka baseline; rollback drill succeeds.
**Rollback:** per-tenant flag back to KAFKA.

---

## Phase 5 — Scale hardening (up to 200M cells)

**Goal:** Prove the largest customer's full-scale runs.

**Work**
- Enable SHARDED + **continue-as-new** waves; tune `targetCellsPerActivity`, `maxUnitsPerWorkflow`, shard size.
- Fairness/rate-limit integration: per-API `K`, global cap via `maxTaskQueueActivitiesPerSecond` + shared token bucket; AIMD backoff on 429s (optional).
- Capacity planning: pods × `S` × `K` vs target completion time and per-API limits.
- History/DB sizing + archival for hours/days-long runs.

**Tests**
- **Load/soak at max:** 200M-cell run (real or high-fidelity stub) to completion; assert 0 lost cells, bounded histories, stable cluster.
- **Chaos at scale:** repeated worker/pod kills during a multi-hour run; assert resume + exactly-once.
- **Rate-limit safety:** assert per-API request rate never exceeds configured limits; no target-endpoint overload.
- **Fairness:** with a global cap, assert ≥ `global/K` APIs progress concurrently; no starvation.

**Exit criteria:** a full 200M-scale run completes correctly under induced faults, within capacity plan, without breaching target-API limits.
**Rollback:** large customers remain on KAFKA until this bar is met.

---

## Phase 6 — Decommission Kafka path

**Goal:** Remove the legacy consumer + heuristic completion once all tenants are migrated.

**Work**
- Migrate remaining tenants to TEMPORAL; confirm no traffic on KAFKA path for a bake period.
- Delete producer/consumer, `TestingStateStore` heuristics, idle-grace/restart logic, per-run topic lifecycle.
- Update docs; remove `NEW_TESTING_ENABLED` Kafka branch.

**Tests**
- **Regression:** full test-run suite on TEMPORAL-only build.
- **Dead-code/config audit:** no references to removed Kafka orchestration paths.
- **Post-removal soak:** production stable for a bake period.

**Exit criteria:** 0 runs on KAFKA for the bake period; TEMPORAL-only build passes full regression; code removed.
**Rollback:** revertable release until the bake period closes; after that, forward-only.

---

## Cross-cutting test strategy

| Layer | What | Where it runs |
|---|---|---|
| Unit | planner, mappers, idempotency keys | CI, every PR |
| Replay | workflow determinism | CI, every PR |
| Integration | end-to-end on dev/staging cluster | CI nightly + pre-release |
| Parity | KAFKA vs TEMPORAL outcome diff | Phase 3 continuous |
| Chaos | worker/pod kills, transient errors | staging + prod canary |
| Load/soak | 2M and 200M runs | pre-Phase-5 exit |
| Rate-limit safety | per-API/global caps | Phase 5 |

## Rollback summary

Every phase is flag-gated and reversible: Phases 0–1 have no production traffic; Phases 2–4 roll back per-collection/per-tenant to KAFKA; Phase 5 keeps large customers on KAFKA until proven; Phase 6 is revertable until the bake period closes.
