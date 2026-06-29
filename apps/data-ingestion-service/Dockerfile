# Java 21 + Jetty 12 EE8 (javax.servlet) + virtual threads for blocking http-proxy/guardrails.
# WAR is still compiled as Java 8 bytecode by the root Maven build; only the runtime changes here.
FROM jetty:12.1.10-jdk21-eclipse-temurin
USER root
RUN apt-get update -y \
    && apt-get install -y --no-install-recommends libpcap-dev \
    && rm -rf /var/lib/apt/lists/*

# EE8 deploys javax.servlet WARs; threadpool-virtual replaces default threadpool.
RUN rm -f /var/lib/jetty/start.d/threadpool.ini \
    && java -jar "$JETTY_HOME/start.jar" \
    --add-modules=ee8-deploy,ee8-webapp,forwarded,threadpool-virtual

ADD ./target/data-ingestion-service.war /var/lib/jetty/webapps/root.war
RUN echo "environment=ee8" > /var/lib/jetty/webapps/root.properties

COPY docker/jetty-start.d/ /var/lib/jetty/start.d/
COPY docker/set-jvm-opts.sh /docker-entrypoint.d/00-set-jvm-opts.sh
RUN chmod +x /docker-entrypoint.d/00-set-jvm-opts.sh

EXPOSE 8080
