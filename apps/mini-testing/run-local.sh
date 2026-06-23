#!/bin/bash

# Set environment variables for local execution
export AKTO_MONGO_CONN="mongodb://localhost:27017/admini"
export AKTO_CONFIG_NAME="staging"
export AKTO_KAFKA_BROKER_URL="localhost:29092"
export AKTO_LOG_LEVEL="INFO"
export DASHBOARD_MODE="local_deploy"
export IS_KUBERNETES="false"
export RUNTIME_MODE="hybrid"

# Set DATABASE_ABSTRACTOR_SERVICE_URL to a dummy URL to avoid production calls
# Use the token from docker.env with accountId=1000000
export DATABASE_ABSTRACTOR_SERVICE_URL="http://localhost:5678"
export DATABASE_ABSTRACTOR_SERVICE_TOKEN="eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoiaW52aXRlX3VzZXIiLCJhY2NvdW50SWQiOjEwMDAwMDAsImlhdCI6MTcxODYzOTUxNSwiZXhwIjoxNzM0NDUwNzE1fQ.jJALXiS_l4_8XAtfJlTFdT8knnqMv62jkE6d7O_VJr8pwzdBjiqSuMGLGy86yGrJPPHOn1efX-byx0roNeaxnEZ4B-giEqI1dYIqSNczXdO9IfUBoYl-lEyzlovEK5gycVXxh5ylGkSdLm-e51IjS76qUt2paynzFoIQYo8hslE3ssXHPhcuTv652LmKixwqSyfpoDR9bYQ-9tNIHFrXspnB4J2XhSCvqW3iOID21NrUW_2tEoTQDTexbRvLJBgvcN5CgO20c5mIvEBFOlOE3ZdYZ7Qc86SJOt7Y4NkK10gk5w8e8DQryF2QW41HmDxqJF7kvUvqwa-OVNUQLmwByg"

# Mini-testing specific config
export MINI_TESTING_NAME="local-mini-testing"
export NEW_TESTING_ENABLED="false"
export SKIP_SSRF_CHECK="true"


echo "Starting mini-testing with local configuration..."
echo "MongoDB: $AKTO_MONGO_CONN"
echo "Kafka: $AKTO_KAFKA_BROKER_URL"
echo "Mini-testing name: $MINI_TESTING_NAME"
echo ""


# exec: replace shell with java so Ctrl+C / SIGTERM reach the JVM
exec java -Xmx2g -XX:+ExitOnOutOfMemoryError \
 -jar target/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar
