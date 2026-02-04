#!/bin/bash

LOG_FILE="/var/log/app/runtime.log"
MAX_LOG_SIZE=${MAX_LOG_SIZE:-104857600}  # Default to 10 MB if not set (10 MB = 10 * 1024 * 1024 bytes)
CHECK_INTERVAL=60                        # Check interval in seconds
MEMORY_RESTART_THRESHOLD=${MEMORY_RESTART_THRESHOLD:-95}  # Restart if memory usage exceeds 95%

# Ensure log directory exists before first use
mkdir -p /var/log/app

# 1. Detect and read cgroup memory limits
if [ -f /sys/fs/cgroup/memory.max ]; then
    # cgroup v2
    MEM_LIMIT_BYTES=$(cat /sys/fs/cgroup/memory.max)
elif [ -f /sys/fs/cgroup/memory/memory.limit_in_bytes ]; then
    # cgroup v1
    MEM_LIMIT_BYTES=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
else
    # Fallback to free -b (bytes) if cgroup file not found
    echo "Neither cgroup v2 nor v1 memory file found, defaulting to free -m" | tee -a "$LOG_FILE"
    # Convert from kB to bytes
    MEM_LIMIT_BYTES=$(free -b | awk '/Mem:/ {print $2}')
fi

# 2. Handle edge cases: "max" means no strict limit or a very large limit
if [ "$MEM_LIMIT_BYTES" = "max" ]; then
    # Arbitrary fallback (1 GiB in bytes here, but adjust as needed)
    echo "Cgroup memory limit set to 'max', defaulting to free memory" | tee -a "$LOG_FILE"
    MEM_LIMIT_BYTES=$(free -b | awk '/Mem:/ {print $2}')
fi

# 3. Convert the memory limit from bytes to MB (integer division)
MEM_LIMIT_MB=$((MEM_LIMIT_BYTES / 1024 / 1024))
echo "Detected container memory limit: ${MEM_LIMIT_MB} MB" | tee -a "$LOG_FILE"

# 4. Calculate 80% of that limit for Xmx
XMX_MEM=$((MEM_LIMIT_MB * 80 / 100))
echo "Calculated -Xmx value: ${XMX_MEM} MB" | tee -a "$LOG_FILE"

# Function to rotate the log file
rotate_log() {
    if [[ -f "$LOG_FILE" && -s "$LOG_FILE" ]]; then
        local log_size
        log_size=$(stat -c%s "$LOG_FILE")  # Get the size of the log file

        if (( log_size >= MAX_LOG_SIZE )); then
            echo "" > "$LOG_FILE"
        fi
    fi
}

# Function to monitor memory usage and restart if necessary
monitor_memory() {
    while true; do
        # Get the current memory usage in bytes
        if [ -f /sys/fs/cgroup/memory.current ]; then
            # cgroup v2
            MEM_USAGE_BYTES=$(cat /sys/fs/cgroup/memory.current)
        elif [ -f /sys/fs/cgroup/memory/memory.usage_in_bytes ]; then
            # cgroup v1
            MEM_USAGE_BYTES=$(cat /sys/fs/cgroup/memory/memory.usage_in_bytes)
        else
            # Fallback to free -b (bytes) if cgroup file not found
            echo "Neither cgroup v2 nor v1 memory file found, defaulting to free -b" | tee -a "$LOG_FILE"
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


# Start monitoring in the background
while true; do
    rotate_log   # Check and rotate logs if necessary
    sleep "$CHECK_INTERVAL"  # Wait for the specified interval before checking again
done &

# Start Java and monitor it
start_java() {
    # Source environment variables from .env file if it exists
    if [ -f /app/.env ]; then
        source /app/.env
    fi

    java -XX:+ExitOnOutOfMemoryError -Xmx${XMX_MEM}m -jar /app/threat-detection-1.0-SNAPSHOT-jar-with-dependencies.jar 2>&1 | tee -a "$LOG_FILE" &

    JAVA_PID=$!
    echo "Started Java with PID: $JAVA_PID" | tee -a "$LOG_FILE"

    monitor_memory &
    MONITOR_PID=$!

    wait "$JAVA_PID"
    echo "Java process exited. Cleaning up memory monitor..." | tee -a "$LOG_FILE"
    kill "$MONITOR_PID" 2>/dev/null
}

while true; do
    start_java
    echo "Restarting Java after crash or memory limit..." | tee -a "$LOG_FILE"
    sleep 2
done

