#!/usr/bin/env bash
# Run mini-testing from the CLI: builds the fat jar, runs it with the same env as the
# VS Code launch config, and tees all output to a timestamped log while streaming to console.
#
# WARNING: this file embeds a DATABASE_ABSTRACTOR_SERVICE_TOKEN. Do NOT commit it.
set -euo pipefail

REPO=/Users/mann/workspace/akto
cd "$REPO"

# --- build (skip with: SKIP_BUILD=1 ./run-mini-testing-local.sh) ---
if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  echo ">> building fat jar..."
  MAVEN_OPTS="" mvn -am -pl apps/mini-testing clean package -DskipTests=true
fi

JAR="apps/mini-testing/target/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar"

# --- runtime env (mirrors launch.json) ---
export NEW_TESTING_ENABLED=true
export KAFKA_BROKER_URL=localhost:9092
export AKTO_LOG_LEVEL=WARN
export RUNTIME_MODE=hybrid
export MINI_TESTING_NAME=local-mann-mac
# export AKTO_MONGO_CONN="mongodb://localhost:27017"   # uncomment if startup complains about Mongo
export DATABASE_ABSTRACTOR_SERVICE_TOKEN="eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoiaW52aXRlX3VzZXIiLCJhY2NvdW50SWQiOjE3MTAxMTg0OTMsInNjb3BlIjpbIk1JTklfVEVTVElORyJdLCJpYXQiOjE3ODcyMDEzODksImV4cCI6MTgwMzA5ODk4OX0.QycrVr9Dk6xNRNbLQp-p3sflMB9kcDzwkAdZdX3CWXfgWzaQLeaYqeT5OuVJLewPentbKge6DQOYLVu20rH4uxOOqYLL_kldzKrvBqry-R_JVk8Dt1jfraLcopQrsWaHcyvNXm1ozkWFLUmIN0fCtruPgWK8zAmqMyb18KgRUZrGo0Vc_53xnIpXf2Y4l_NmjQkJlPFxk3oGR2Bbdy40-blAS0jRkRBNz0lkFyZxGRnEBKSt5iq-1pKBBlCLopYcaFfCsBnwnb-K4KZM80UO6zS0JtcbagDK2N1TwBOnRVcvB0JsQZlWzNhBFPVlTGDitPP90wlgWoM7gUYGJs5OcA"

# --- resource limits (override via env) ---
# JVM heap ceiling. Total RSS ~= heap + metaspace + thread stacks + off-heap, so keep heap
# a couple GB under your target: XMX=6g lands the process around ~8GB.
XMX="${XMX:-6g}"
XMS="${XMS:-2g}"
# Soft CPU cap: makes the JVM (GC/JIT/ForkJoinPool/availableProcessors) behave as if N cores.
# NOTE: test concurrency is driven by maxConcurrentRequest config, not this flag.
ACTIVE_CPUS="${ACTIVE_CPUS:-2}"

LOG="mini-testing-$(date +%Y%m%d-%H%M%S).log"
echo ">> running (heap=$XMX cpus=$ACTIVE_CPUS), logging to $LOG (Ctrl-C to stop)"
java -Xmx"$XMX" -Xms"$XMS" -XX:ActiveProcessorCount="$ACTIVE_CPUS" -jar "$JAR" 2>&1 | tee "$LOG"
