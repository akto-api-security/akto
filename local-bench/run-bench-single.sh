#!/usr/bin/env bash
# Exp A — isolated single-threaded per-cell cost (no Kafka, no pool, no contention).
# Runs com.akto.testing.BenchSingleTest against REAL apis+templates built via the producer's own
# init mechanism (TestExecutor.init with doInitOnly=true). Answers: intrinsic cost vs contention.
#
# Usage:  local-bench/run-bench-single.sh <summaryId> [numApis] [capSec] [csvSubcats|ALL] [jfr]
#   e.g.  local-bench/run-bench-single.sh 6a8c141599d091d3e61d48e1 3 400
#         local-bench/run-bench-single.sh 6a8c141599d091d3e61d48e1 3 400 FUZZ_USER_ID jfr
set -euo pipefail

SUMMARY="${1:?usage: run-bench-single.sh <summaryId> [numApis] [capSec] [csvSubcats|ALL] [jfr]}"
NUM_APIS="${2:-3}"
CAP_SEC="${3:-400}"
SUBCATS="${4:-}"
JFR="${5:-}"

REPO=/Users/mann/workspace/akto
cd "$REPO"

[[ -f local-bench/bench.env ]] || { echo "!! create local-bench/bench.env first"; exit 1; }
# shellcheck disable=SC1091
source local-bench/bench.env

# Java 17 (current branch target); build == run JDK.
if grep -q '<source>8</source>' apps/mini-testing/pom.xml 2>/dev/null; then
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home
else
  unset JAVA_HOME || true
fi
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"

echo ">> mvn package (fat jar)"
MAVEN_OPTS="" mvn -am -pl apps/mini-testing clean package -DskipTests=true -q
JAR=apps/mini-testing/target/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar
[[ -f "$JAR" ]] || { echo "!! jar not found: $JAR"; exit 1; }

export NEW_TESTING_ENABLED=true
export AKTO_LOG_LEVEL=WARN
export RUNTIME_MODE=hybrid

TS=$(date +%Y%m%d-%H%M%S)
LOG="local-bench/single-${SUMMARY}-${TS}.log"
JFR_ARGS=()
if [[ "$JFR" == "jfr" ]]; then
  JFR_ARGS=(-XX:StartFlightRecording=duration=0,filename="local-bench/single-${SUMMARY}-${TS}.jfr",settings=profile)
  echo ">> JFR on -> local-bench/single-${SUMMARY}-${TS}.jfr"
fi

echo ">> RUN: $("$JAVA_BIN" -version 2>&1 | head -1)"
echo ">> args: summary=$SUMMARY numApis=$NUM_APIS capSec=$CAP_SEC subcats=[${SUBCATS:-<default 17>}]"
echo ">> log -> $LOG"
"$JAVA_BIN" -Xmx"${XMX:-6g}" -Xms"${XMS:-2g}" ${JFR_ARGS[@]+"${JFR_ARGS[@]}"} \
  -cp "$JAR" com.akto.testing.BenchSingleTest "$SUMMARY" "$NUM_APIS" "$CAP_SEC" "$SUBCATS" 2>&1 | tee "$LOG"
echo ">> done. log: $LOG"
