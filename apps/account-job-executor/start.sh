#!/bin/bash

echo "=========================================="
echo "Starting Account Job Executor Service"
echo "=========================================="

# Validate required environment variables
if [ -z "$DATABASE_ABSTRACTOR_SERVICE_URL" ]; then
    echo "ERROR: DATABASE_ABSTRACTOR_SERVICE_URL is not set"
    exit 1
fi

if [ -z "$DATABASE_ABSTRACTOR_SERVICE_TOKEN" ]; then
    echo "ERROR: DATABASE_ABSTRACTOR_SERVICE_TOKEN is not set"
    exit 1
fi

echo "Configuration:"
echo "  Cyborg URL: $DATABASE_ABSTRACTOR_SERVICE_URL"
echo "  Log Level: ${AKTO_LOG_LEVEL:-INFO}"
echo "=========================================="

# Run the JAR
exec java \
    -Xms512m \
    -Xmx1024m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -jar /app/account-job-executor.jar
