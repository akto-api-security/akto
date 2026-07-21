FROM jetty:9.4-jre8
USER root
RUN apt-get update -y
RUN apt-get install -y --no-install-recommends libpcap-dev procps
# Upgrade OpenSSL/libssl to the patched build (CVE-2026-45447).
RUN apt-get install -y --only-upgrade openssl libssl3t64
# Swap the JRE 8 for JRE 17 in place. The jetty:9.4-jre8 image is built on
# eclipse-temurin:8-jre with java at /opt/java/openjdk, so overlaying the temurin:17
# runtime there keeps Jetty's existing JAVA_HOME/PATH working with no other changes.
COPY --from=eclipse-temurin:17-jre /opt/java/openjdk /opt/java/openjdk
# Java 17 removed the extension mechanism; the leftover jre8 lib/ext dir makes the JVM
# refuse to start ("extension mechanism no longer supported"), so drop it.
RUN rm -rf /opt/java/openjdk/lib/ext
ADD ./target/dashboard.war /var/lib/jetty/webapps/root.war
RUN echo "--module=http-forwarded" > /var/lib/jetty/start.d/http-forwarded.ini
RUN echo "jetty.httpConfig.sendServerVersion=false" > /var/lib/jetty/start.d/server.ini
RUN echo "org.slf4j.simpleLogger.log.org.eclipse.jetty.annotations.AnnotationParser=ERROR" >> /var/lib/jetty/start.d/server.ini

COPY set_xmx.sh /var/lib/jetty/set_xmx.sh
RUN chmod +x /var/lib/jetty/set_xmx.sh

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
EXPOSE 8080