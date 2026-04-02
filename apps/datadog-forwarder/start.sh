#!/bin/bash

LOG_FILE="/var/log/app/runtime.log"
MAX_LOG_SIZE=${MAX_LOG_SIZE:-104857600}  # Default to 100 MB
CHECK_INTERVAL=60
MEMORY_RESTART_THRESHOLD=${MEMORY_RESTART_THRESHOLD:-95}

mkdir -p /var/log/app

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
XMX_MEM=$((MEM_LIMIT_MB * 80 / 100))

rotate_log() {
    if [[ -f "$LOG_FILE" && -s "$LOG_FILE" ]]; then
        local log_size
        log_size=$(stat -c%s "$LOG_FILE")
        if (( log_size >= MAX_LOG_SIZE )); then
            echo "" > "$LOG_FILE"
        fi
    fi
}

monitor_memory() {
    while true; do
        if [ -f /sys/fs/cgroup/memory.current ]; then
            MEM_USAGE_BYTES=$(cat /sys/fs/cgroup/memory.current)
        elif [ -f /sys/fs/cgroup/memory/memory.usage_in_bytes ]; then
            MEM_USAGE_BYTES=$(cat /sys/fs/cgroup/memory/memory.usage_in_bytes)
        else
            MEM_USAGE_BYTES=$(free -b | awk '/Mem:/ {print $3}')
        fi

        MEM_USAGE_PERCENT=$((MEM_USAGE_BYTES * 100 / MEM_LIMIT_BYTES))

        if (( MEM_USAGE_PERCENT >= MEMORY_RESTART_THRESHOLD )); then
            echo "Memory usage exceeded ${MEMORY_RESTART_THRESHOLD}%. Restarting application..." | tee -a "$LOG_FILE"
            kill -9 "$JAVA_PID" 2>/dev/null
            break
        fi

        sleep "$CHECK_INTERVAL"
    done
}

while true; do
    rotate_log
    sleep "$CHECK_INTERVAL"
done &

start_java() {
    java -XX:+ExitOnOutOfMemoryError -Xmx${XMX_MEM}m -jar /app/datadog-forwarder-1.0-SNAPSHOT-jar-with-dependencies.jar 2>&1 | tee -a "$LOG_FILE" &

    JAVA_PID=$!
    echo "Started Datadog Forwarder with PID: $JAVA_PID" | tee -a "$LOG_FILE"

    monitor_memory &
    MONITOR_PID=$!

    wait "$JAVA_PID"
    echo "Java process exited. Cleaning up memory monitor..." | tee -a "$LOG_FILE"
    kill "$MONITOR_PID" 2>/dev/null
}

while true; do
    start_java
    echo "Restarting Datadog Forwarder after crash or memory limit..." | tee -a "$LOG_FILE"
    sleep 2
done
