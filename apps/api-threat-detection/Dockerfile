FROM openjdk

WORKDIR /app
COPY ./target/api-threat-detection-1.0-SNAPSHOT-jar-with-dependencies.jar /app/api-threat-detection-1.0-SNAPSHOT-jar-with-dependencies.jar
COPY start.sh /app/start.sh

RUN chmod +x /app/start.sh

CMD ["/app/start.sh"]
