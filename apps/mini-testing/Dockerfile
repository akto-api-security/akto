FROM eclipse-temurin:17-jre-resolute
WORKDIR /app

COPY ./target/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar /app/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh
CMD ["/app/start.sh"]
