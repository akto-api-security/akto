#!/bin/bash

# Run the startup script to set JAVA_OPTIONS
source /var/lib/jetty/start.sh

# Start Jetty normally
exec /docker-entrypoint.sh "$@"