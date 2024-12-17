#!/bin/bash

# Get total memory in MB
TOTAL_MEM=$(free -m | awk '/Mem:/ {print $2}')

# Print the total memory for debugging
echo "Total memory available: ${TOTAL_MEM} MB"

# Calculate 80% of total memory
XMX_MEM=$((TOTAL_MEM * 80 / 100))

# Print the calculated XMX value for debugging
echo "Calculated -Xmx value: ${XMX_MEM} MB"

# Run Java with dynamically calculated -Xmx value
exec java -XX:+ExitOnOutOfMemoryError -Xmx${XMX_MEM}m -jar /app/source-code-analyser-1.0-SNAPSHOT-jar-with-dependencies.jar
