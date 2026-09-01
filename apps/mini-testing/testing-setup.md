# mini-testing perf investigation — setup & operational notes

Operational companion that keeps getting rebuilt after every `/compact`. Read this first.
It covers ONLY: how to run, where output lands, how to read it, and how to prove a claim.
**No findings / no RCA live here** — those go in the dated investigation reports
(`21-aug-investigation.md`, `24aug-*`). Code architecture lives in `CLAUDE.md`.

---

## 1. How to run a benchmark

```
local-bench/run-bench.sh <git-ref> <label> [max-minutes]
# e.g. local-bench/run-bench.sh fix/testing-stops-running 24aug-subcategory-api-stall-1 60
```

Prereqs (all external, must be up before the run):
- Kafka up; abstractor (ultron) reachable — no 403/422 on token exchange.
- A test run **TRIGGERED** in the UI for `MINI_TESTING_NAME` with the same scope, BEFORE
  invoking. Producer fans out M×N messages to `akto.test.messages`; consumer drains.
- `local-bench/bench.env` exists (from `bench.env.template`). Holds
  `DATABASE_ABSTRACTOR_SERVICE_TOKEN` (JWT) — **never commit**. `local-bench/` is self-gitignored.

What the script does: checks out the ref, picks JDK from `apps/mini-testing/pom.xml`
(`<source>8</source>` → Zulu 8, else 17 — build JDK MUST == run JDK), `mvn -am -pl
apps/mini-testing clean package`, runs the fat jar with `NEW_TESTING_ENABLED=true`,
`AKTO_LOG_LEVEL=WARN`, `RUNTIME_MODE=hybrid`, `-Xmx6g`. Auto-stops on `TESTRUN END` or
`max-minutes`. Also launches `diagnose.sh` alongside.

Env knobs: `XMX`/`XMS` (heap), `DIAG_INTERVAL` (diag sampling seconds, default 20).

Build-only sanity check (fast, no run):
`mvn -pl apps/mini-testing compile -DskipTests=true`

---

## 2. Where output lands (and the #1 gotcha)

Per run, timestamped `local-bench/{run,diag}-<label>-<YYYYMMDD-HHMMSS>.log`:
- `run-*.log`  — full JVM stdout+stderr. Contains the `TESTRUN *` metric lines.
- `diag-*.log` — the `diagnose.sh` sampler table (see §4).
- `hang-<ts>.txt` — auto-captured jstack bundle when diag detects a stall (see §4).

### ⚠️ GOTCHA: run logs contain embedded NUL bytes → grep sees them as binary
Some test payloads log raw `0x00` (e.g. `Unexpected char 0x00 in x-request-id`). That puts
NUL bytes in `run-*.log`, so `file` reports "AKT archive data" and **plain `grep` silently
matches nothing** (prints "0", exits 1). ALWAYS use `grep -a` on `run-*.log`:

```
grep -a "TESTRUN COST"            run-*.log
grep -a "TESTRUN STALL-BY-SUBCAT" run-*.log
```

Do not trust a bare `grep` returning empty on `run-*.log`.

---

## 3. The metric lines (TestRunMetrics → warnAndAddToDb → both stdout AND ultron DB)

`TestRunMetrics.java` `tick()` runs once per drain-loop iteration (`ConsumerUtil.java:445`)
and emits on cadence:

- `TESTRUN START / CONSUMER-UP / PROGRESS / RESTART / END` — lifecycle + throughput.
- `TESTRUN COST` — per-test wall-time breakdown ("what is taking so long"). Fields:
  `n` (completed tests), `avgPerTestMs`, `requestsPerTest`, `cpuPerTestMs`, then `RUN_TEST=…ms`
  split into `RESOLVE_EXPR / SEND_REQUEST / VALIDATE / OTHER`, plus
  `LOOKUP / PERSIST_LOGS / INSERT_RESULTS / ULTRON_TOTAL`.
  - Sub-phase timing = `TestPhaseTimer` (thread-local, written at call sites in
    `ExecutorAlgorithm` (resolveExpr) and `Executor` (sendRequest/validate)). Recorded in a
    `try/finally` around `runTestNew` (ConsumerUtil ~162-167).
  - **KNOWN LIMIT:** the finally records only when `runTestNew` RETURNS. A test stuck the full
    300s hasn't returned, so it is NOT in `n` / the COST average ⇒ COST reflects *completers
    only*. Cross-check against STALL/diag for the in-flight (stuck) population.
- `TESTRUN STALL` — fires when `progress_frozen` OR `slots_clogged` (≥half the 200-slot pool
  held by tasks older than `STUCK_AGE_MS`). Includes `oldestTasks=[…]` (top-15 oldest in-flight,
  each `{age, subcategory, api, thread}`), plus:
  - `TESTRUN STALL-BY-SUBCAT bySubcat=[SUBCAT=count, …]` — currently-stuck slots by subcategory.
  - `TESTRUN STALL-BY-API   byApi=[apiKey path method=count, …]` — currently-stuck slots by API.
  These are a **snapshot** of the in-flight registry (not a cumulative counter).

Handy extractions (always `-a`):
```
grep -a "TESTRUN COST" run-*.log | sed -E 's/.*TESTRUN COST/COST/'
# dominant stuck API per snapshot over time:
grep -a "TESTRUN STALL-BY-API" run-*.log | sed -E 's/^([0-9-]+ [0-9:]+).*byApi=\[[^ ]+ ([^ ]+ [A-Z]+)=([0-9]+).*/\1  top=\2  count=\3/'
```

---

## 4. diagnose.sh — the live bottleneck sampler

Every `DIAG_INTERVAL`s it jstacks the JVM and prints one table row:
```
time cpu% | rate done avgMs stuck tmout err | workers io/cl/cpu/pk/ot | states R/W/T/B | kafkaLag
```
- `workers io/cl/cpu/pk/ot` = **`mini-test-worker-*`** threads bucketed by TOP FRAME:
  - `io`  = `Net.poll|socketRead|NioSocketImpl|SSLSocketImpl.*read`
  - `cl`  = `callAcquirePooledConnection|RealConnectionPool` (waiting for HTTP conn)
  - `cpu` = `resolveWordListVar|java.util.regex|StringLatin1|Pattern` (word-list cartesian)
  - `pk`  = `Unsafe.park`
  - `ot`  = everything else
- `states R/W/T/B` = RUNNABLE/WAITING/TIMED_WAITING/BLOCKED histogram for worker+pc pools.
- On a sustained stall it writes `hang-<ts>.txt`: 3 jstacks 4s apart + state histogram +
  "top frame dump 1 vs 3 (identical ⇒ genuinely stuck)" + hottest lock contention.

### Known classifier bugs (flagged, NOT fixed)
- `SSLSocketImpl.decode` counted `other`, should be `io` (regex only catches `SSLSocketImpl.*read`).
- `Arrays.copyOfRange` counted `other`, should be `cpu`.
⇒ the `cpu`/`io` buckets are floors (understated); `other` is inflated. Treat the buckets as a
lead, then PROVE via §6 before claiming anything.

---

## 5. Thread architecture (critical for reading STALL vs diag correctly)

Two pools, both sized `maxConcurrentRequest` (=200 here), 1:1 coupled:

1. **`pc-pool-*`** — Confluent parallel-consumer callback threads. Each: parses the record,
   calls `metrics.onSubmit(recordId, subcat, api, threadName)` **with its OWN (pc-pool) name**,
   `executor.submit(runTestFromMessage)`, then **blocks in `future.get(300s)`**
   (ConsumerUtil ~355-377).
2. **`mini-test-worker-*`** — `Executors.newFixedThreadPool(200)`; actually runs `runTestNew`.
   Executor/ApiExecutor ERROR logs and diagnose.sh's worker buckets are THIS pool.

Consequences for reading data:
- `TESTRUN STALL oldestTasks[].thread = pc-pool-*` is the **waiter** blocked in `future.get`,
  NOT where work happens. Do not jstack-interpret pc-pool frames as the bottleneck.
- diagnose.sh `workers io/cl/cpu` = **mini-test-worker** = the real execution signal.
- One stuck test burns BOTH a pc-pool slot and a worker slot (pools equal-sized ⇒ no decoupling).
- Test timeout = `maxRunTimeForTests` (300s). On timeout: `markTimedOut()`, `future.cancel(true)`,
  `createTimedOutResultFromMessage` writes a `TEST_TIMED_OUT` terminal result to DB.

---

## 6. RULE: every bottleneck claim MUST be proven by correlating a metric line with jstack or JFR

Metric lines (§3) and diag buckets (§4) are **leads, never proof**. `COST` sees only completers;
diag buckets key on a single top frame and have known misclassifications. Before asserting *where*
time goes, correlate against a real thread-level artifact. Do not spin an RCA from the metric lines
alone.

### Correlating with jstack (the fast path)
1. From a `TESTRUN STALL` line, take a concrete stuck cell — a `{subcategory, api}` from
   `oldestTasks` — and note its `age` (should be near 300s).
2. Grab ≥3 jstacks of the **live** JVM ~4s apart (or use the auto `hang-<ts>.txt`):
   `JSTACK=$("$(/usr/libexec/java_home -v 17)"/bin/jstack); for k in 1 2 3; do "$JSTACK" <pid> > /tmp/j.$k.txt; sleep 4; done`
3. Look at the **`mini-test-worker-*`** threads (NOT `pc-pool-*` — those are just parked in
   `future.get`; see §5). For a genuine CPU grind the top frame must be IDENTICAL across dumps 1
   and 3 AND state `RUNNABLE` (a `RUNNABLE` socket read is I/O, not CPU — check the frame, not just
   the state). For a block, expect `WAITING`/`TIMED_WAITING`/socket-read frames.
4. Tie the thread back to the cell: the worker running a given record can be matched via the
   `Thread [...] picked up record recordId=...` / `finished processing recordId=...` info logs, or
   by matching the frame's test-template class to the stuck subcategory.
5. A claim is proven only when the STALL cell, its ~300s age, and the repeated worker-thread frame
   all point at the same code path. State which dumps/lines you used.

### Correlating with JFR (when you need CPU time & allocation, not just a snapshot)
- Enable on the run (add to the `java` line in `run-bench.sh`, or set once):
  `-XX:StartFlightRecording=duration=0,filename=local-bench/rec-<label>.jfr,settings=profile`
- After the run: `jfr print --events jdk.ExecutionSample rec-*.jfr` (hot methods by CPU),
  `jfr print --events jdk.ObjectAllocationSample rec-*.jfr` (allocation hot spots),
  `jfr view hot-methods rec-*.jfr`, `jfr view thread-cpu-load rec-*.jfr`.
- Correlate the same way: the JFR hot method / allocating stack must match the code path implied
  by the STALL cell and the diag `cpu`/`io` bucket. JFR settles CPU-vs-blocked definitively
  (`ExecutionSample` counts on-CPU samples; blocked threads don't accrue them), which jstack alone
  can't — a socket read shows `RUNNABLE` in jstack but produces no CPU samples in JFR.

Report the artifact (dump filenames / jfr file + event) alongside any bottleneck claim.

---

## 7. Hard constraints
- Never commit/push unless explicitly asked. `DATABASE_ABSTRACTOR_SERVICE_TOKEN` must never be
  committed. Working branch: `fix/testing-stops-running`. MongoDB queries: one query per line.
- Profiling rule: every claimed bottleneck must be backed by a tool measurement (§6) — no spun RCA.
