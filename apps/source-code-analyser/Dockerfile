# TODO: openjdk:latest is no more. upgrade when using this module.
FROM openjdk
WORKDIR /app
COPY ./target/source-code-analyser-1.0-SNAPSHOT-jar-with-dependencies.jar /app/source-code-analyser-1.0-SNAPSHOT-jar-with-dependencies.jar
CMD "java" "-XX:+ExitOnOutOfMemoryError" "-jar" "/app/source-code-analyser-1.0-SNAPSHOT-jar-with-dependencies.jar"