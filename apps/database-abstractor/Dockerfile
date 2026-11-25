FROM jetty:9.4-jre8
USER root
RUN apt-get update -y
# CVE-2026-111497: also upgrade libbrotli1 to the patched Ubuntu build (USN-8956-1).
# jetty:9.4 is EOL and its base image is not refreshed, so patch it here.
RUN apt-get install -y --no-install-recommends libpcap-dev \
    && apt-get install -y --only-upgrade --no-install-recommends libbrotli1
ADD ./target/database-abstractor.war /var/lib/jetty/webapps/root.war
RUN echo "--module=http-forwarded" > /var/lib/jetty/start.d/http-forwarded.ini
RUN echo "jetty.httpConfig.sendServerVersion=false" > /var/lib/jetty/start.d/server.ini
RUN echo "org.slf4j.simpleLogger.log.org.eclipse.jetty.annotations.AnnotationParser=ERROR" >> /var/lib/jetty/start.d/server.ini
EXPOSE 9000