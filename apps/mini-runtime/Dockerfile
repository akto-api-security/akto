# TODO: openjdk:latest is no more. upgrade when using this module.
FROM openjdk
WORKDIR /app
COPY ./target/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar /app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar
CMD "java" "-XX:+ExitOnOutOfMemoryError" "-jar" "/app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar"