FROM openjdk
WORKDIR /app
COPY ./target/api-threat-detection-1.0-SNAPSHOT-jar-with-dependencies.jar /app/api-threat-detection-1.0-SNAPSHOT-jar-with-dependencies.jar
CMD "java" "-XX:+ExitOnOutOfMemoryError" "-jar" "/app/api-threat-detection-1.0-SNAPSHOT-jar-with-dependencies.jar"