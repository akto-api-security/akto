# Java 21 + Jetty 12 EE8 (javax.servlet) + virtual threads for blocking http-proxy/guardrails.
# WAR is still compiled as Java 8 bytecode by the root Maven build; only the runtime changes here.
FROM jetty:12.1.10-jdk21-eclipse-temurin
USER root
RUN apt-get update -y \
    && apt-get install -y --no-install-recommends libpcap-dev \
    && rm -rf /var/lib/apt/lists/*

# EE8 deploys javax.servlet WARs; threadpool-virtual runs servlet work on virtual threads.
RUN java -jar "$JETTY_HOME/start.jar" \
    --add-modules=ee8-deploy,ee8-webapp,http,http-forwarded,threadpool-virtual \
    && rm -f /var/lib/jetty/start.d/threadpool.ini

ADD ./target/data-ingestion-service.war /var/lib/jetty/webapps/root.war
RUN echo "environment=ee8" > /var/lib/jetty/webapps/root.properties

RUN echo "jetty.httpConfig.sendServerVersion=false" > /var/lib/jetty/start.d/server.ini \
    && echo "org.slf4j.simpleLogger.log.org.eclipse.jetty.ee8.webapp=WARN" >> /var/lib/jetty/start.d/server.ini

COPY docker/jetty-start.d/ /var/lib/jetty/start.d/
COPY docker/set-jvm-opts.sh /docker-entrypoint.d/00-set-jvm-opts.sh
RUN chmod +x /docker-entrypoint.d/00-set-jvm-opts.sh

EXPOSE 8080
