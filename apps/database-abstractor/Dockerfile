FROM jetty:9.4-jre8-alpine
USER root
RUN apk update
RUN apk add --no-cache libpcap-dev procps
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