FROM amazoncorretto:8-alpine-jre
WORKDIR /app
COPY ./target/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar /app/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar
CMD "java" "-XX:+ExitOnOutOfMemoryError" "-jar" "/app/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar"
