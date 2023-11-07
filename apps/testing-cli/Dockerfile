FROM amazoncorretto:8
WORKDIR /app
COPY ./target/testing-cli-1.0-SNAPSHOT-jar-with-dependencies.jar /app/testing-cli-1.0-SNAPSHOT-jar-with-dependencies.jar
CMD "java" "-XX:+ExitOnOutOfMemoryError" "-jar" "/app/testing-cli-1.0-SNAPSHOT-jar-with-dependencies.jar"