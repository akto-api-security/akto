#!/bin/bash

LOG_FILE="/tmp/dump.log"
MAX_LOG_SIZE=${MAX_LOG_SIZE:-10485760}  # Default to 10 MB if not set (10 MB = 10 * 1024 * 1024 bytes)
CHECK_INTERVAL=60                        # Check interval in seconds

# Function to rotate the log file
rotate_log() {
    if [[ -f "$LOG_FILE" && -s "$LOG_FILE" ]]; then
        local log_size
        log_size=$(stat -c%s "$LOG_FILE")  # Get the size of the log file

        if (( log_size >= MAX_LOG_SIZE )); then
            mv "$LOG_FILE" "$LOG_FILE.old"  # Rename current log file
            touch "$LOG_FILE"                # Create a new empty log file
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
        exec java -XX:+ExitOnOutOfMemoryError -jar /app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar
    } >> "$LOG_FILE" 2>&1 
else
    exec java -XX:+ExitOnOutOfMemoryError -jar /app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar
fi
