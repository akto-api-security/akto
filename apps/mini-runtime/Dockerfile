FROM openjdk
WORKDIR /app
COPY ./target/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar /app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar
CMD "java" "-Xmx2500m" "-XX:+ExitOnOutOfMemoryError" "-jar" "/app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar"