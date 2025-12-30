#!/bin/bash
RUNTIME_LOG_FILE="/var/log/app/runtime.log"
MAX_LOG_SIZE=${MAX_LOG_SIZE:-104857600}  # Default to 100 MB if not set (100 MB = 100 * 1024 * 1024 bytes)
CHECK_INTERVAL=60                        # Check interval in seconds
MEMORY_RESTART_THRESHOLD=${MEMORY_RESTART_THRESHOLD:-95}  # Restart if memory usage exceeds 95%

# 1. Detect and read cgroup memory limits
if [ -f /sys/fs/cgroup/memory.max ]; then
    # cgroup v2
    MEM_LIMIT_BYTES=$(cat /sys/fs/cgroup/memory.max)
elif [ -f /sys/fs/cgroup/memory/memory.limit_in_bytes ]; then
    # cgroup v1
    MEM_LIMIT_BYTES=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
else
    # Fallback to free -b (bytes) if cgroup file not found
    echo "Neither cgroup v2 nor v1 memory file found, defaulting to free -m" | tee -a "$RUNTIME_LOG_FILE"
    # Convert from kB to bytes
    MEM_LIMIT_BYTES=$(free -b | awk '/Mem:/ {print $2}')
fi

# 2. Handle edge cases: "max" means no strict limit or a very large limit
if [ "$MEM_LIMIT_BYTES" = "max" ]; then
    # Arbitrary fallback (1 GiB in bytes here, but adjust as needed)
    echo "Cgroup memory limit set to 'max', defaulting to free memory" | tee -a "$RUNTIME_LOG_FILE"
    MEM_LIMIT_BYTES=$(free -b | awk '/Mem:/ {print $2}')
fi

# 3. Convert the memory limit from bytes to MB (integer division)
MEM_LIMIT_MB=$((MEM_LIMIT_BYTES / 1024 / 1024))
echo "Detected container memory limit: ${MEM_LIMIT_MB} MB" | tee -a "$RUNTIME_LOG_FILE"

# 4. Calculate 80% of that limit for Xmx
XMX_MEM=$((MEM_LIMIT_MB * 80 / 100))
echo "Calculated -Xmx value: ${XMX_MEM} MB" | tee -a "$RUNTIME_LOG_FILE"

# Function to rotate the log file
rotate_log() {
    # Rotate runtime.log if it exists and exceeds size limit
    if [[ -f "$RUNTIME_LOG_FILE" && -s "$RUNTIME_LOG_FILE" ]]; then
        local runtime_log_size
        runtime_log_size=$(stat -c%s "$RUNTIME_LOG_FILE")  # Get the size of the runtime log file

        if (( runtime_log_size >= MAX_LOG_SIZE )); then
            echo "$(date): Rotating runtime.log (size: ${runtime_log_size} bytes)"
            echo "" > "$RUNTIME_LOG_FILE"
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
            echo "Neither cgroup v2 nor v1 memory file found, defaulting to free -b" | tee -a "$RUNTIME_LOG_FILE"
            MEM_USAGE_BYTES=$(free -b | awk '/Mem:/ {print $3}')
        fi

        MEM_USAGE_PERCENT=$((MEM_USAGE_BYTES * 100 / MEM_LIMIT_BYTES))

        if (( MEM_USAGE_PERCENT >= MEMORY_RESTART_THRESHOLD )); then
            echo "Memory usage exceeded ${MEMORY_RESTART_THRESHOLD}%. Restarting application..." | tee -a "$RUNTIME_LOG_FILE"
            kill -9 "$JAVA_PID" 2>/dev/null
            break
        fi

        sleep "$CHECK_INTERVAL"
    done
}


# Start log rotation in the background
while true; do
    rotate_log   # Check and rotate logs if necessary
    sleep "$CHECK_INTERVAL"  # Wait for the specified interval before checking again
done &

# Start Java and monitor it
start_java() {
    # Ensure log directory exists
    mkdir -p /var/log/app

    java -XX:+ExitOnOutOfMemoryError -Xmx${XMX_MEM}m -jar /app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar 2>&1 | tee -a "$RUNTIME_LOG_FILE" &


    JAVA_PID=$!
    echo "Started Java with PID: $JAVA_PID" | tee -a "$RUNTIME_LOG_FILE"

    monitor_memory &
    MONITOR_PID=$!

    wait "$JAVA_PID"
    echo "Java process exited. Cleaning up memory monitor..." | tee -a "$RUNTIME_LOG_FILE"
    kill "$MONITOR_PID" 2>/dev/null
}

while true; do
    start_java
    echo "Restarting Java after crash or memory limit..." | tee -a "$RUNTIME_LOG_FILE"
    sleep 2
done
