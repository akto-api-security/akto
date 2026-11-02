# mini-testing branch benchmark

End-to-end A/B benchmark of two mini-testing branches, compared from their
`TESTRUN` log streams (both branches emit the identical format).

## One-time setup
```bash
cp local-bench/bench.env.template local-bench/bench.env
# edit local-bench/bench.env: paste abstractor token, set broker/mongo/name
```

## Run each branch (same workload, same env)
Trigger a test run for your MINI_TESTING_NAME with the SAME scope before each,
then:
```bash
# NOTE: use the metrics-ported BRANCH for 1.70.6, not the raw tag
# (the tag has no TestRunMetrics -> no TESTRUN logs to compare).
local-bench/run-bench.sh investigate/mini-testing-1.70.6-fast-run fast 60
# re-trigger the same run, then:
local-bench/run-bench.sh fix/testing-stops-running slow 60
```
Each builds with the right JDK (1.70.6 → Java 8, current → Java 17), runs against
your real Kafka/abstractor/targets, tees to `local-bench/run-<label>-<ts>.log`,
and auto-stops on `TESTRUN END` (or the max-minutes arg).

## Compare
```bash
local-bench/compare-bench.py run-fast-*.log run-slow-*.log \
    --label-a fast_1.70.6 --label-b slow_current
```
Headline = **wall-clock to process the first N tests** (N = largest count both
runs reached) — an equal-work number robust to the slow branch not finishing.
Also reports steady throughput, per-test latency, timeouts, peak stuckSlots, and
a verdict.

## Fairness notes
- Same scope, same targets, same machine, same `XMX`/`XMS` for both runs.
- Run back-to-back (or interleave 2–3x each and take medians) so target-side
  drift doesn't dominate. A ~10x gap is far above that noise.
- `time to N` includes the producer cold-start (that's branch code too, so it's
  fair to include); `steady tests/s` excludes it (pure consumer throughput).

## Files
- `run-bench.sh` — build+run+capture one branch
- `compare-bench.py` — parse two logs → metrics table + verdict
- `bench.env` — your secrets/config (gitignored)
- `kafka-test-messages.jsonl` — 226,740-msg dump of `akto.test.messages` (insurance)
