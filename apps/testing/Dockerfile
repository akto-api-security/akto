FROM amazoncorretto:8
WORKDIR /app
COPY ./target/testing-1.0-SNAPSHOT-jar-with-dependencies.jar /app/testing-1.0-SNAPSHOT-jar-with-dependencies.jar
COPY ./src/main/resources/nuclei_linux /app/nuclei_linux
COPY ./src/main/resources/nuclei_m1 /app/nuclei_m1
CMD "java" "-XX:+ExitOnOutOfMemoryError" "-jar" "/app/testing-1.0-SNAPSHOT-jar-with-dependencies.jar"
