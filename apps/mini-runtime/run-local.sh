#!/bin/bash


# Set environment variables for local execution
export AKTO_MONGO_CONN="mongodb://localhost:27017/admini"
export AKTO_CONFIG_NAME="staging"
export AKTO_KAFKA_TOPIC_NAME="akto.api.logs"
export AKTO_KAFKA_BROKER_URL="localhost:29092"
export AKTO_KAFKA_GROUP_ID_CONFIG="asdf-local"
export AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG="100"
export AKTO_KAFKA_LOG_TOPIC_NAME="akto.api.producer.logs"
export AKTO_KAFKA_HEARTBEAT_TOPIC_NAME="akto.daemonset.producer.heartbeats"
export AKTO_ACCOUNT_NAME="Helios"
export DASHBOARD_MODE="local_deploy"
export IS_KUBERNETES="false"
export RUNTIME_MODE="hybrid"
export AKTO_LOG_LEVEL="INFO"


# Set DATABASE_ABSTRACTOR_SERVICE_URL to a dummy URL to avoid production calls
# Use the token from docker.env with accountId=1000000
export DATABASE_ABSTRACTOR_SERVICE_URL="http://localhost:5678"
export OVERRIDE_DATABASE_ABSTRACTOR_SERVICE_URL="http://localhost:5678"
export DATABASE_ABSTRACTOR_SERVICE_TOKEN="eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoiaW52aXRlX3VzZXIiLCJhY2NvdW50SWQiOjEwMDAwMDAsImlhdCI6MTc4MDQxNTE1MX0.movtUl22AlLj1topz7_m1PNLqKFwZlZXTtvEMUumg-VxRwmG0_JcgVcqIUNDReuerZbRP7MiyN8rTN_bXRMZIL4TZP4Jamr08Yf3MY0CMX2zxiVLhwVuaFS446jZVmM5Mq3EhPwGNGWN_z0dTp2E_Jym8EYG-Fa1uL2Rw4OSajmHr7_Y5CzG3a6tgQ1eWCYLpWsaGKwVWZ-Iuq68nYdeHaN_G9ZcYMOQhClc17ARS6V-HFwMK2c_IBR-60yMxehdGyqdWrkERYID4ZfcOCPeq7m2eiUkKtDLZlmKHF8SqyoB6N5SqWJMRyFui5nxZ-T3IAkZ6KACaIKo6Us4b5T-iA"




echo "Starting mini-runtime with local configuration..."
echo "MongoDB: $AKTO_MONGO_CONN"
echo "Kafka: $AKTO_KAFKA_BROKER_URL"
echo ""


# exec: replace shell with java so Ctrl+C / SIGTERM reach the JVM (avoids stray processes)
exec java -Xmx2g -XX:+ExitOnOutOfMemoryError \
 -jar target/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar
