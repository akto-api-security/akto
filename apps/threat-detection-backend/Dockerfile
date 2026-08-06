FROM eclipse-temurin:17-jre-resolute
WORKDIR /app

RUN apt-get update -y \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/*

COPY ./target/threat-detection-backend-1.0-SNAPSHOT-jar-with-dependencies.jar /app/threat-detection-backend-1.0-SNAPSHOT-jar-with-dependencies.jar

COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh
CMD ["/app/start.sh"]
