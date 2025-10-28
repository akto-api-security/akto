FROM openjdk
WORKDIR /app

# Update system packages
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY ./target/api-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar /app/api-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar

COPY start.sh /app/start.sh

RUN chmod +x /app/start.sh

CMD ["/app/start.sh"]