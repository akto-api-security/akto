# Java 21 + Jetty 12 EE8 (javax.servlet) + virtual threads for blocking http-proxy/guardrails.
# WAR is still compiled as Java 8 bytecode by the root Maven build; only the runtime changes here.
FROM jetty:12.1.10-jdk21-eclipse-temurin
USER root
# Retry apt-get update: arm64 cross-builds can hit transient Ubuntu mirror sync errors.
RUN set -eux; \
    echo 'Acquire::Retries "5";' > /etc/apt/apt.conf.d/80-retries; \
    for i in 1 2 3 4 5; do \
      apt-get update -y && break; \
      if [ "$i" -eq 5 ]; then exit 1; fi; \
      sleep 15; \
    done; \
    apt-get install -y --no-install-recommends libpcap-dev; \
    rm -rf /var/lib/apt/lists/*

# EE8 deploys javax.servlet WARs; ee8-jsp satisfies Struts JspTemplateEngine classpath (Jetty 9 had this built-in).
RUN rm -f /var/lib/jetty/start.d/threadpool.ini \
    && java -jar "$JETTY_HOME/start.jar" \
    --add-modules=ee8-deploy,ee8-webapp,ee8-jsp,forwarded,threadpool-virtual

ADD ./target/data-ingestion-service.war /var/lib/jetty/webapps/root.war
RUN echo "environment=ee8" > /var/lib/jetty/webapps/root.properties

COPY docker/jetty-start.d/ /var/lib/jetty/start.d/
COPY docker/set-jvm-opts.sh /docker-entrypoint.d/00-set-jvm-opts.sh
RUN chmod +x /docker-entrypoint.d/00-set-jvm-opts.sh \
    && chown -R jetty:jetty /var/lib/jetty/webapps /var/lib/jetty/start.d

USER jetty
EXPOSE 8080
