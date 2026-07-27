#!/bin/bash

echo "Running startup script..."

# Export JAVA_OPTIONS so Jetty picks it up.
# --add-opens uses the single-token "=" form: Jetty splits space-separated options into
# separate tokens, which breaks "--add-opens X=Y" (it becomes two args). Java 17 strong
# encapsulation opens needed by reflective libs (Mongo POJO codec, Struts/OGNL, etc.).
export JAVA_OPTIONS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.time=ALL-UNNAMED"

# Log the final JAVA_OPTIONS value
echo "JAVA_OPTIONS set to: $JAVA_OPTIONS"