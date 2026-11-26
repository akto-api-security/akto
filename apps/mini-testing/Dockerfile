FROM eclipse-temurin:17-jre
WORKDIR /app

COPY ./target/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar /app/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar
COPY start.sh /app/start.sh
# Run as a non-root user. Only /app (cwd; NEW_TESTING_ENABLED creates testing-info/ here) and /var/log/app
# (start.sh's log file) need to be writable; the jar and start.sh stay root-owned. Group 0 gets
# write too, so an arbitrary UID with group 0 (OpenShift) also works.
RUN chmod +x /app/start.sh \
    && mkdir -p /var/log/app \
    && chown 65532:0 /app /var/log/app \
    && chmod 775 /app /var/log/app
USER 65532:65532
CMD ["/app/start.sh"]
