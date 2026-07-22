
FROM jetty:9.4-jre8
USER root
# Swap the baked-in Java 8 for a maintained Java 17 JRE at the same path.
# Jetty layout, /docker-entrypoint.sh, modules, port and launch stay untouched.
COPY --from=eclipse-temurin:17-jre /opt/java/openjdk /opt/java/openjdk
# COPY merges over the old Java 8 JRE but doesn't delete files Java 17 dropped.
# Java 17 removed the extension mechanism, so the stale Java-8 lib/ext lingers and
# Jetty 9.4 passes -Djava.ext.dirs for it -> "Could not create the JVM". Remove it.
RUN rm -rf /opt/java/openjdk/lib/ext
RUN apt-get update -y
RUN apt-get install -y --no-install-recommends libpcap-dev procps
# Patch OS libs to clear Grype High CVEs (openssl/libssl -> 3.0.13-0ubuntu3.11)
RUN apt-get install -y --only-upgrade openssl libssl3t64
# Remove unused JASPI module jar (not enabled; clears its High CVE)
RUN rm -f /usr/local/jetty/lib/jetty-jaspi-*.jar
ADD ./target/database-abstractor.war /var/lib/jetty/webapps/root.war
RUN echo "--module=http-forwarded" > /var/lib/jetty/start.d/http-forwarded.ini
RUN echo "jetty.httpConfig.sendServerVersion=false" > /var/lib/jetty/start.d/server.ini
RUN echo "org.slf4j.simpleLogger.log.org.eclipse.jetty.annotations.AnnotationParser=ERROR" >> /var/lib/jetty/start.d/server.ini

COPY set_xmx.sh /var/lib/jetty/set_xmx.sh
RUN chmod +x /var/lib/jetty/set_xmx.sh

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
EXPOSE 9000
