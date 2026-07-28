FROM eclipse-temurin:17-jre-noble
WORKDIR /app

RUN apt-get update -y \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends python3 \
    && rm -rf /var/lib/apt/lists/*

COPY ./target/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar /app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh
CMD ["/app/start.sh"]