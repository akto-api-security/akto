FROM jetty:9.4-jre8
USER root

# Install dependencies
RUN apt-get update -y && \
    apt-get install -y --no-install-recommends \
    libpcap-dev \
    curl \
    ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Install Vector
RUN curl --proto '=https' --tlsv1.2 -sSfL https://sh.vector.dev | bash -s -- -y && \
    mv /root/.vector/bin/vector /usr/local/bin/

# Create log directory
RUN mkdir -p /var/log/akto && chown jetty:jetty /var/log/akto

# Copy application WAR
ADD ./target/data-ingestion-service.war /var/lib/jetty/webapps/root.war

# Copy Vector configuration
COPY ./vector.toml /etc/vector/vector.toml

# Configure Jetty
RUN echo "--module=http-forwarded" > /var/lib/jetty/start.d/http-forwarded.ini
RUN echo "jetty.httpConfig.sendServerVersion=false" > /var/lib/jetty/start.d/server.ini
RUN echo "org.slf4j.simpleLogger.log.org.eclipse.jetty.annotations.AnnotationParser=ERROR" >> /var/lib/jetty/start.d/server.ini

# Create startup script to run both Jetty and Vector
RUN echo '#!/bin/bash\n\
vector --config /etc/vector/vector.toml &\n\
exec /docker-entrypoint.sh "$@"' > /start.sh && \
    chmod +x /start.sh

EXPOSE 9091

ENTRYPOINT ["/start.sh"]
CMD ["java", "-jar", "/usr/local/jetty/start.jar"]