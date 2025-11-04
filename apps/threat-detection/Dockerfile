FROM amazoncorretto:8
WORKDIR /app
COPY ./target/threat-detection-1.0-SNAPSHOT-jar-with-dependencies.jar /app/threat-detection-1.0-SNAPSHOT-jar-with-dependencies.jar
CMD "java" "-XX:+ExitOnOutOfMemoryError" "-jar" "/app/threat-detection-1.0-SNAPSHOT-jar-with-dependencies.jar"
