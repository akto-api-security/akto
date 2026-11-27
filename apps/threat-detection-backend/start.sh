#!/bin/bash

# Size the heap from the container memory limit (same approach as apps/dashboard/set_xmx.sh
# and apps/threat-detection/start.sh): -Xmx = 80% of the limit, or OVERWRITE_XMX (MB) if set.

if [ -n "$OVERWRITE_XMX" ]; then
    XMX_MEM="$OVERWRITE_XMX"
    echo "Using OVERWRITE_XMX: ${XMX_MEM} MB"
else
    if [ -f /sys/fs/cgroup/memory.max ]; then
        # cgroup v2
        MEM_LIMIT_BYTES=$(cat /sys/fs/cgroup/memory.max)
    elif [ -f /sys/fs/cgroup/memory/memory.limit_in_bytes ]; then
        # cgroup v1
        MEM_LIMIT_BYTES=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
    else
        echo "Neither cgroup v2 nor v1 memory file found, defaulting to free -b"
        MEM_LIMIT_BYTES=$(free -b 2>/dev/null | awk '/Mem:/ {print $2}')
    fi

    # cgroup v2 reports "max" for no limit; cgroup v1 reports a huge number. Use host memory in both cases.
    HOST_MEM_BYTES=$(free -b 2>/dev/null | awk '/Mem:/ {print $2}')
    if [ "$MEM_LIMIT_BYTES" = "max" ] || { [ -n "$HOST_MEM_BYTES" ] && [ "$MEM_LIMIT_BYTES" -gt "$HOST_MEM_BYTES" ] 2>/dev/null; }; then
        echo "Cgroup memory limit is unset/above host memory, defaulting to host memory"
        MEM_LIMIT_BYTES="$HOST_MEM_BYTES"
    fi

    if [ -n "$MEM_LIMIT_BYTES" ] && [ "$MEM_LIMIT_BYTES" -gt 0 ] 2>/dev/null; then
        MEM_LIMIT_MB=$((MEM_LIMIT_BYTES / 1024 / 1024))
        XMX_MEM=$((MEM_LIMIT_MB * 80 / 100))
        echo "Detected container memory limit: ${MEM_LIMIT_MB} MB, calculated -Xmx value: ${XMX_MEM} MB"
    else
        # Detection failed: keep the JVM default heap sizing rather than pass a bogus -Xmx.
        echo "Could not detect memory limit, not setting -Xmx"
    fi
fi

XMX_OPT=""
[ -n "$XMX_MEM" ] && XMX_OPT="-Xmx${XMX_MEM}m"

# --add-opens: Java 17 strong-encapsulation opens needed by reflective libraries
# (MongoDB POJO codec, etc.). Single-token "=" form.
exec java \
  -XX:+ExitOnOutOfMemoryError \
  -XX:-UseBASE64Intrinsics \
  $XMX_OPT \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=java.base/java.time=ALL-UNNAMED \
  -jar /app/threat-detection-backend-1.0-SNAPSHOT-jar-with-dependencies.jar
