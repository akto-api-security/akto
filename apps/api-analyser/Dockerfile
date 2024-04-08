FROM openjdk
WORKDIR /app
COPY ./target/api-analyser-1.0-SNAPSHOT-jar-with-dependencies.jar /app/api-analyser-1.0-SNAPSHOT-jar-with-dependencies.jar
CMD "java" "-XX:+ExitOnOutOfMemoryError" "-jar" "/app/api-analyser-1.0-SNAPSHOT-jar-with-dependencies.jar"
