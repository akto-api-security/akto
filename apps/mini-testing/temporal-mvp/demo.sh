#!/bin/bash
# Showcase: execution planner + live run with a mid-run worker CRASH + recovery.
set -u
cd "$(dirname "$0")"
export PATH="/opt/homebrew/bin:$PATH"
CP="target/mvp.jar"
PKG="com.akto.testing.temporal"

echo "########################################################"
echo "# adaptive execution planner (one code path)"
echo "########################################################"
java -cp "$CP" $PKG.PlanDemo

echo
echo "########################################################"
echo "# live run + mid-run worker crash + recovery"
echo "########################################################"
rm -rf data worker1.log worker2.log run.log
export CUSTOMER="acme" TEST_RUN_ID="demo-crash" NUM_APIS=120 TESTS_PER_API=50 \
       LATENCY_MS=150 CONCURRENT_TESTS_PER_API=6 FAIL_RATE=0.07 TIMEOUT_RATE=0.03

echo "[demo] starting worker for customer=acme ..."
CONCURRENT_BATCHES=6 java -cp "$CP" $PKG.WorkerMain > worker1.log 2>&1 &
W1=$!
for i in $(seq 1 30); do grep -q "started" worker1.log 2>/dev/null && break; sleep 1; done
echo "[demo] $(cat worker1.log)"

echo "[demo] starting run ..."
java -cp "$CP" $PKG.RunStarter > run.log 2>&1 &
R=$!

sleep 10
echo "[demo] >>>>>> CRASH: kill -9 worker (pid $W1) mid-run <<<<<<"
kill -9 "$W1" 2>/dev/null
sleep 6

echo "[demo] >>>>>> RESTART: new worker for customer=acme <<<<<<"
CONCURRENT_BATCHES=6 java -cp "$CP" $PKG.WorkerMain > worker2.log 2>&1 &
W2=$!

echo "[demo] waiting for run to finish (Temporal retries the failed API-groups on the new worker) ..."
wait $R

echo
echo "===================== run.log ====================="
cat run.log
kill "$W2" 2>/dev/null
echo "[demo] done."
