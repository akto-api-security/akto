#!/usr/bin/env bash
# End-to-end benchmark run of one mini-testing branch/tag.
# Builds the fat jar with the JDK that branch needs, runs it against your real
# Kafka/abstractor/targets, tees the TESTRUN log stream to local-bench/, and
# auto-stops when the run finishes (TESTRUN END) or a max wall-clock is hit.
#
# Usage:  local-bench/run-bench.sh <git-ref> <label> [max-minutes]
#   e.g.  local-bench/run-bench.sh vmini-testing-1.70.6            fast   60
#         local-bench/run-bench.sh fix/testing-stops-running       slow   60
#
# Prereqs: Kafka up, abstractor reachable (no 403s), and a test run TRIGGERED
# for MINI_TESTING_NAME with the SAME scope before each invocation.
set -euo pipefail

REF="${1:?usage: run-bench.sh <git-ref> <label> [max-minutes]}"
LABEL="${2:?label required (e.g. fast|slow)}"
MAX_MIN="${3:-60}"

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"

JAR_NAME="mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar"

# TERM, then (after a grace period) KILL any mini-testing JVM matching the jar.
# The app catches SIGTERM (shutdown hook) and can linger, so we escalate to -9.
kill_jvms() {
  local pids; pids=$(pgrep -f "$JAR_NAME" 2>/dev/null || true)
  [[ -z "$pids" ]] && return 0
  echo ">> stopping mini-testing JVM(s): $pids"
  kill $pids 2>/dev/null || true
  for _ in $(seq 1 12); do pgrep -f "$JAR_NAME" >/dev/null 2>&1 || return 0; sleep 1; done
  pids=$(pgrep -f "$JAR_NAME" 2>/dev/null || true)
  [[ -n "$pids" ]] && { echo ">> forcing SIGKILL: $pids"; kill -9 $pids 2>/dev/null || true; }
  return 0
}
# Never leave an orphaned JVM behind (covers Ctrl-C, errors, and normal exit).
trap kill_jvms EXIT INT TERM

echo ">> killing any stray mini-testing instances before starting"
kill_jvms

if [[ ! -f local-bench/bench.env ]]; then
  echo "!! create local-bench/bench.env from bench.env.template first"; exit 1
fi
# shellcheck disable=SC1091
source local-bench/bench.env

echo ">> checkout $REF"
#git switch --detach "$REF" 2>/dev/null || git switch "$REF"
git checkout $REF
echo ">> HEAD: $(git rev-parse --short HEAD)  ($(git describe --tags --always 2>/dev/null || true))"

# Pick JDK from the module's compiler target (1.70.6 => Java 8, current => Java 17).
# CRITICAL: build AND run with that same major. 70.6 needs Java 8's built-in Nashorn;
# running its jar on Java 17 makes getEngineByName("nashorn") null (auth scripts break).
if grep -q '<source>8</source>' apps/mini-testing/pom.xml 2>/dev/null; then
  BUILD_TARGET=8
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home
else
  BUILD_TARGET=17
  unset JAVA_HOME || true
fi
# Run binary comes from JAVA_HOME (not PATH), so build JDK == run JDK.
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"

# --- hard guard: abort if the run JVM major != build target (never silently run on the wrong JDK) ---
RUN_VER_LINE="$("$JAVA_BIN" -version 2>&1 | head -1 || true)"
if echo "$RUN_VER_LINE" | grep -q '"1\.8'; then RUN_MAJOR=8
else RUN_MAJOR="$(echo "$RUN_VER_LINE" | grep -oE '"[0-9]+' | tr -d '"' | head -1)"; fi
echo ">> build target = Java $BUILD_TARGET ; JAVA_BIN=$JAVA_BIN ; version: $RUN_VER_LINE"
if [[ "$RUN_MAJOR" != "$BUILD_TARGET" ]]; then
  echo "!! ABORT: run JVM is Java '$RUN_MAJOR' but build target is Java $BUILD_TARGET."
  echo "!! (check that $JAVA_HOME exists / zulu-8 is installed). Not running."
  exit 1
fi
echo ">> verified: building AND running on Java $RUN_MAJOR"

echo ">> mvn package (this can take a few minutes)"
MAVEN_OPTS="" mvn -am -pl apps/mini-testing clean package -DskipTests=true -q

JAR=apps/mini-testing/target/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar
[[ -f "$JAR" ]] || { echo "!! jar not found: $JAR"; exit 1; }

TS=$(date +%Y%m%d-%H%M%S)
LOG="local-bench/run-${LABEL}-${TS}.log"

export NEW_TESTING_ENABLED=true
export AKTO_LOG_LEVEL=WARN
export RUNTIME_MODE=hybrid

echo ">> RUN jvm: $("$JAVA_BIN" -version 2>&1 | head -1)"
echo ">> running -> $LOG   (auto-stop on TESTRUN END or ${MAX_MIN}m)"
"$JAVA_BIN" -Xmx"${XMX:-6g}" -Xms"${XMS:-2g}" -jar "$JAR" > "$LOG" 2>&1 &
PID=$!

# auto-start the live bottleneck sampler; stream it to the console AND a file (self-exits when JVM dies)
DIAG_LOG="local-bench/diag-${LABEL}-${TS}.log"
echo ">> live diagnostics (also saved to $DIAG_LOG):"
( local-bench/diagnose.sh "${DIAG_INTERVAL:-20}" 2>&1 | tee "$DIAG_LOG" ) &
DIAG_PID=$!

deadline=$(( $(date +%s) + MAX_MIN * 60 ))
saw_start=0
while kill -0 "$PID" 2>/dev/null; do
  grep -q "TESTRUN START" "$LOG" 2>/dev/null && saw_start=1
  if [[ $saw_start -eq 1 ]] && grep -q "TESTRUN END" "$LOG" 2>/dev/null; then
    echo ">> TESTRUN END seen — stopping"; break
  fi
  if [[ "$(date +%s)" -ge "$deadline" ]]; then echo ">> max ${MAX_MIN}m reached — stopping"; break; fi
  sleep 5
done

kill_jvms
kill "${DIAG_PID:-}" 2>/dev/null || true
pkill -f 'local-bench/diagnose.sh' 2>/dev/null || true

echo ">> finished. log: $LOG"
echo ">> TESTRUN lines:"; grep "TESTRUN" "$LOG" | tail -3
echo ">> diagnostics summary (bucket table): $DIAG_LOG"
tail -8 "$DIAG_LOG" 2>/dev/null
echo "$LOG"
