FROM jetty:9.4-jre8
USER root
RUN apt-get update -y
RUN apt-get install -y --no-install-recommends libpcap-dev
ADD ./target/data-ingestion-service.war /var/lib/jetty/webapps/root.war
RUN echo "--module=http-forwarded" > /var/lib/jetty/start.d/http-forwarded.ini
RUN echo "jetty.httpConfig.sendServerVersion=false" > /var/lib/jetty/start.d/server.ini
RUN echo "org.slf4j.simpleLogger.log.org.eclipse.jetty.annotations.AnnotationParser=ERROR" >> /var/lib/jetty/start.d/server.ini
EXPOSE 9091