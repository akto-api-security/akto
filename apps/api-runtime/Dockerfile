FROM openjdk
WORKDIR /app
COPY ./target/api-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar /app/api-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar
CMD "java" "-XX:+ExitOnOutOfMemoryError" "-jar" "/app/api-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar"