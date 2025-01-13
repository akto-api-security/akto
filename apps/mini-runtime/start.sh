#!/bin/bash

LOG_FILE="/tmp/dump.log"
MAX_LOG_SIZE=${MAX_LOG_SIZE:-10485760}  # Default to 10 MB if not set (10 MB = 10 * 1024 * 1024 bytes)
CHECK_INTERVAL=60                        # Check interval in seconds

# 1. Detect and read cgroup memory limits
if [ -f /sys/fs/cgroup/memory.max ]; then
    # cgroup v2
    MEM_LIMIT_BYTES=$(cat /sys/fs/cgroup/memory.max)
elif [ -f /sys/fs/cgroup/memory/memory.limit_in_bytes ]; then
    # cgroup v1
    MEM_LIMIT_BYTES=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
else
    # Fallback to free -b (bytes) if cgroup file not found
    echo "Neither cgroup v2 nor v1 memory file found, defaulting to free -m"
    # Convert from kB to bytes
    MEM_LIMIT_BYTES=$(free -b | awk '/Mem:/ {print $2}')
fi

# 2. Handle edge cases: "max" means no strict limit or a very large limit
if [ "$MEM_LIMIT_BYTES" = "max" ]; then
    # Arbitrary fallback (1 GiB in bytes here, but adjust as needed)
    echo "Cgroup memory limit set to 'max', defaulting to free memory"
    MEM_LIMIT_BYTES=$(free -b | awk '/Mem:/ {print $2}')
fi

# 3. Convert the memory limit from bytes to MB (integer division)
MEM_LIMIT_MB=$((MEM_LIMIT_BYTES / 1024 / 1024))
echo "Detected container memory limit: ${MEM_LIMIT_MB} MB"

# 4. Calculate 80% of that limit for Xmx
XMX_MEM=$((MEM_LIMIT_MB * 80 / 100))
echo "Calculated -Xmx value: ${XMX_MEM} MB"

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

if [[ "${ENABLE_LOGS}" == "false" ]]; then
    # Start monitoring in the background
    while true; do
        rotate_log   # Check and rotate logs if necessary
        sleep "$CHECK_INTERVAL"  # Wait for the specified interval before checking again
    done &
    {
        exec java -XX:+ExitOnOutOfMemoryError -Xmx${XMX_MEM}m -jar /app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar
    } >> "$LOG_FILE" 2>&1 
else
    exec java -XX:+ExitOnOutOfMemoryError -Xmx${XMX_MEM}m -jar /app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar
fi
