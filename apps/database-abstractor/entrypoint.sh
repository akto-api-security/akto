#!/bin/bash

# Run the memory calculation script to set JAVA_OPTIONS
source /opt/jetty/set_xmx.sh

# Start Jetty
exec java $JAVA_OPTIONS -jar /opt/jetty/start.jar