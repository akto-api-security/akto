#!/bin/bash
# Auto-size JVM heap from cgroup/container memory for data-ingestion Jetty.
# Mirrors apps/dashboard/set_xmx.sh with G1 + smaller stacks for large thread pools.

set -e

if [ -f /sys/fs/cgroup/memory.max ]; then
    MEM_LIMIT_BYTES=$(cat /sys/fs/cgroup/memory.max)
elif [ -f /sys/fs/cgroup/memory/memory.limit_in_bytes ]; then
    MEM_LIMIT_BYTES=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
else
    MEM_LIMIT_BYTES=$(free -b | awk '/Mem:/ {print $2}')
fi

if [ "$MEM_LIMIT_BYTES" = "max" ]; then
    MEM_LIMIT_BYTES=$(free -b | awk '/Mem:/ {print $2}')
fi

MEM_LIMIT_MB=$((MEM_LIMIT_BYTES / 1024 / 1024))
XMX_MEM=$((MEM_LIMIT_MB * 75 / 100))
XMS_MEM=$((MEM_LIMIT_MB * 50 / 100))

NUM_CPUS=$(nproc 2>/dev/null || getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)
PARALLEL_GC_THREADS=$((NUM_CPUS * 3 / 4))
[ "$PARALLEL_GC_THREADS" -lt 2 ] && PARALLEL_GC_THREADS=2
CONC_GC_THREADS=$((PARALLEL_GC_THREADS / 2))
[ "$CONC_GC_THREADS" -lt 1 ] && CONC_GC_THREADS=1

export JAVA_OPTIONS="-XX:+ExitOnOutOfMemoryError \
-Xms${XMS_MEM}m -Xmx${XMX_MEM}m -Xss512k \
-XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
-XX:ParallelGCThreads=${PARALLEL_GC_THREADS} -XX:ConcGCThreads=${CONC_GC_THREADS}"

echo "data-ingestion JVM: ${MEM_LIMIT_MB}MB limit → -Xms${XMS_MEM}m -Xmx${XMX_MEM}m (${NUM_CPUS} CPUs, ParallelGCThreads=${PARALLEL_GC_THREADS})"
echo "JAVA_OPTIONS=${JAVA_OPTIONS}"
