FROM amazoncorretto:8

# Install packages
RUN yum install -y wget tar gzip libpcap procps

# Download and install jetty
RUN mkdir /opt/jetty
RUN wget https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-distribution/9.4.36.v20210114/jetty-distribution-9.4.36.v20210114.tar.gz
RUN tar -xzvf jetty-distribution-9.4.36.v20210114.tar.gz
RUN rm -rf jetty-distribution-9.4.36.v20210114.tar.gz
RUN mv jetty-distribution-9.4.36.v20210114 jetty
RUN mv jetty /opt/

# Configure Jetty user and clean up install
RUN yum install -y /usr/sbin/adduser
RUN useradd jetty && \
    chown -R jetty:jetty /opt/jetty && \
    rm -rf /opt/jetty/webapps.demo
RUN mkdir -p /opt/jetty/start.d
ADD ./target/database-abstractor.war /opt/jetty/webapps/root.war
RUN echo "--module=http-forwarded" > /opt/jetty/start.d/http-forwarded.ini
RUN echo "jetty.httpConfig.sendServerVersion=false" > /opt/jetty/start.d/server.ini
RUN echo "org.slf4j.simpleLogger.log.org.eclipse.jetty.annotations.AnnotationParser=ERROR" >> /opt/jetty/start.d/server.ini

COPY set_xmx.sh /opt/jetty/set_xmx.sh
RUN chmod +x /opt/jetty/set_xmx.sh

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Set defaults for docker run
WORKDIR /opt/jetty
EXPOSE 9000

ENTRYPOINT ["/entrypoint.sh"]