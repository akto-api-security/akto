FROM eclipse-temurin:17-jre-noble

RUN apt-get update -y \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends python3 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY ./target/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar /app/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh
CMD ["/app/start.sh"]
