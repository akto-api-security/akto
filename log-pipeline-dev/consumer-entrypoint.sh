#!/bin/bash
# Builds database-abstractor from the mounted checkout (unless SKIP_BUILD=true), then runs the consumer main class.
set -euo pipefail
cd /workspace

WAR_DIR=apps/database-abstractor/target/database-abstractor   # exploded war (finalName=database-abstractor)

if [ "${SKIP_BUILD:-false}" != "true" ]; then
  echo "[log-consumer] building (same invocation as CI: mvn package -pl apps/database-abstractor -am -DskipTests)"
  # Concurrent replicas share the bind-mounted target/; take a lock so only one builds at a time.
  mkdir -p /workspace/log-pipeline-dev/.build
  exec 9>/workspace/log-pipeline-dev/.build/lock
  flock 9
  mvn -B -q package -pl apps/database-abstractor -am -DskipTests
  flock -u 9
else
  echo "[log-consumer] SKIP_BUILD=true, reusing existing build in $WAR_DIR"
fi

if [ ! -d "$WAR_DIR/WEB-INF/lib" ]; then
  echo "[log-consumer] ERROR: $WAR_DIR/WEB-INF/lib missing; run once without SKIP_BUILD" >&2
  exit 1
fi

MAIN="${LOG_CONSUMER_MAIN:-com.akto.logpipeline.LogPipelineConsumerMain}"
echo "[log-consumer] starting $MAIN"
# shellcheck disable=SC2086
exec java ${JAVA_OPTS:-} -cp "$WAR_DIR/WEB-INF/classes:$WAR_DIR/WEB-INF/lib/*" "$MAIN"
