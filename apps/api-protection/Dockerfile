FROM openjdk
WORKDIR /app
COPY ./target/api-protection-1.0-SNAPSHOT-jar-with-dependencies.jar /app/api-protection-1.0-SNAPSHOT-jar-with-dependencies.jar
CMD "java" "-XX:+ExitOnOutOfMemoryError" "-jar" "/app/api-protection-1.0-SNAPSHOT-jar-with-dependencies.jar"