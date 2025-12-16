# Use Eclipse Temurin JRE 8 (Debian-based)
FROM eclipse-temurin:8-jre

WORKDIR /app

# Copy the JAR file
COPY ./target/account-job-executor-1.0-SNAPSHOT-jar-with-dependencies.jar /app/account-job-executor.jar

# Copy start script
COPY start.sh /app/start.sh

# Make start script executable
RUN chmod +x /app/start.sh

# Set default environment variables (can be overridden)
ENV DATABASE_ABSTRACTOR_SERVICE_URL="" \
    DATABASE_ABSTRACTOR_SERVICE_TOKEN="" \
    AZURE_BINARY_STORAGE_CONNECTION_STRING="" \
    AZURE_BINARY_BLOB_URL="" \
    AKTO_LOG_LEVEL="INFO"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD pgrep -f account-job-executor || exit 1

# Run the service
CMD ["/app/start.sh"]
