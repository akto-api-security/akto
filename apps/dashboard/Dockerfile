
FROM amazoncorretto:8

# Install packages
RUN yum install -y wget tar gzip

# Download and install jetty
ENV JETTY_VERSION 9.4.15
ENV RELEASE_DATE v20190215
RUN mkdir /opt/jetty
RUN wget https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-distribution/9.4.36.v20210114/jetty-distribution-9.4.36.v20210114.tar.gz
RUN ls
RUN ls
RUN tar -xzvf jetty-distribution-9.4.36.v20210114.tar.gz
RUN rm -rf jetty-distribution-9.4.36.v20210114.tar.gz
RUN mv jetty-distribution-9.4.36.v20210114 jetty
RUN mv jetty /opt/

# Configure Jetty user and clean up install
RUN yum install -y /usr/sbin/adduser
RUN useradd jetty && \
    chown -R jetty:jetty /opt/jetty && \
    rm -rf /opt/jetty/webapps.demo
RUN mkdir /opt/jetty/start.d
ADD ./target/dashboard.war /opt/jetty/webapps/root.war
RUN ls /opt/jetty/
RUN echo "--module=http-forwarded" >> /opt/jetty/start.d/http-forwarded.ini
RUN echo "jetty.httpConfig.sendServerVersion=false" >> /opt/jetty/start.d/server.ini
RUN echo "org.slf4j.simpleLogger.log.org.eclipse.jetty.annotations.AnnotationParser=ERROR" >> /opt/jetty/start.d/server.ini

# Set defaults for docker run
WORKDIR /opt/jetty
EXPOSE 8080
CMD ["java", "-jar", "start.jar", "jetty.home=/opt/jetty"]
