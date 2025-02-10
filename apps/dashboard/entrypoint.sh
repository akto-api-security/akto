#!/bin/bash

# Run the memory calculation script to set JAVA_OPTIONS
source /var/lib/jetty/set_xmx.sh

# Start Jetty normally
exec /docker-entrypoint.sh "$@"
