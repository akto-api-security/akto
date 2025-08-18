#!/bin/bash

echo "Running memory detection script..."

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
    MEM_LIMIT_BYTES=$(free -b | awk '/Mem:/ {print $2}')
fi

# 2. Handle edge cases: "max" means no strict limit or a very large limit
if [ "$MEM_LIMIT_BYTES" = "max" ]; then
    echo "Cgroup memory limit set to 'max', defaulting to free memory"
    MEM_LIMIT_BYTES=$(free -b | awk '/Mem:/ {print $2}')
fi

# 3. Convert the memory limit from bytes to MB (integer division)
MEM_LIMIT_MB=$((MEM_LIMIT_BYTES / 1024 / 1024))
echo "Detected container memory limit: ${MEM_LIMIT_MB} MB"

# 4. Calculate 80% of that limit for Xmx
XMX_MEM=$((MEM_LIMIT_MB * 80 / 100))
echo "Calculated -Xmx value: ${XMX_MEM} MB"

# Export JAVA_OPTIONS so Jetty picks it up
export JAVA_OPTIONS="-XX:+ExitOnOutOfMemoryError -Xmx${XMX_MEM}m"

# Log the final JAVA_OPTIONS value
echo "JAVA_OPTIONS set to: $JAVA_OPTIONS"