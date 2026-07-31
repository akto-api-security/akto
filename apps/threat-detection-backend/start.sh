#!/bin/bash

# --add-opens: Java 17 strong-encapsulation opens needed by reflective libraries
# (MongoDB POJO codec, etc.). Single-token "=" form.
exec java \
  -XX:+ExitOnOutOfMemoryError \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=java.base/java.time=ALL-UNNAMED \
  -jar /app/threat-detection-backend-1.0-SNAPSHOT-jar-with-dependencies.jar
