FROM amazoncorretto:8
WORKDIR /app
COPY ./target/threat-detection-1.0-SNAPSHOT-jar-with-dependencies.jar /app/threat-detection-1.0-SNAPSHOT-jar-with-dependencies.jar
COPY ./start.sh /app/start.sh
RUN chmod +x /app/start.sh
CMD ["/app/start.sh"]
