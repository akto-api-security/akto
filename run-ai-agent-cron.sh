#!/bin/bash

echo "Starting AI Agent Discovery Cron..."
echo "MongoDB: $AKTO_MONGO_CONN"
echo "Account ID: $AKTO_ACCOUNT_ID"
echo ""

cd apps

# Build api-runtime first (required dependency)
echo "Building api-runtime..."
mvn clean install -pl api-runtime -am -DskipTests=true

if [ $? -ne 0 ]; then
    echo "api-runtime build failed!"
    exit 1
fi

cd dashboard

# Build just the standalone cron using Maven (this will compile dependencies)
echo "Building standalone cron..."
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt -DskipTests=true -q

if [ $? -ne 0 ]; then
    echo "Failed to build classpath!"
    exit 1
fi

# Compile the specific standalone classes
echo "Compiling standalone classes..."
CLASSPATH=$(cat cp.txt):../api-runtime/target/classes:../../libs/dao/target/classes

javac -d target/classes -cp "$CLASSPATH" \
  src/main/java/com/akto/standalone/AiAgentDiscoveryStandalone.java \
  src/main/java/com/akto/utils/crons/AiAgentDiscoveryCron.java

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "Starting cron..."
java -cp "target/classes:$CLASSPATH" \
  com.akto.standalone.AiAgentDiscoveryStandalone

rm -f cp.txt
