FROM amazoncorretto:8
WORKDIR /app
COPY ./target/testing-job-1.0-SNAPSHOT-jar-with-dependencies.jar /app/testing-job-1.0-SNAPSHOT-jar-with-dependencies.jar
CMD ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/testing-job-1.0-SNAPSHOT-jar-with-dependencies.jar"]
