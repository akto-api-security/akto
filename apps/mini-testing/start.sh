#!/bin/bash

# --add-opens: Java 17 strong-encapsulation opens needed by reflective libraries
# (MongoDB POJO codec, etc.). Single-token "=" form.
exec java \
  -XX:+ExitOnOutOfMemoryError \
  -XX:MaxRAMPercentage=${MAX_RAM_PCT:-75.0} \
  ${JAVA_OPTS:-} \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=java.base/java.time=ALL-UNNAMED \
  -jar /app/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar
