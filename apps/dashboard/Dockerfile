
FROM java:8-jdk

# Install packages
RUN apt-get update && \ 
    apt-get update --fix-missing && \ 
    apt-get install -y wget

# Download and install jetty
ENV JETTY_VERSION 9.4.15
ENV RELEASE_DATE v20190215
RUN mkdir /opt/jetty
RUN wget http://download.eclipse.org/jetty/stable-9/dist/jetty-distribution-${JETTY_VERSION}.${RELEASE_DATE}.tar.gz && \
    tar -xzvf jetty-distribution-${JETTY_VERSION}.${RELEASE_DATE}.tar.gz && \
    rm -rf jetty-distribution-${JETTY_VERSION}.${RELEASE_DATE}.tar.gz && \
    mv jetty-distribution-${JETTY_VERSION}.${RELEASE_DATE}/ /opt/jetty

# Configure Jetty user and clean up install
RUN useradd jetty && \
    chown -R jetty:jetty /opt/jetty && \
    rm -rf /opt/jetty/webapps.demo

ADD ./target/dashboard.war /opt/jetty/webapps/root.war
RUN echo "--module=http-forwarded" >> /opt/jetty/start.d/http-forwarded.ini
RUN echo "jetty.httpConfig.sendServerVersion=false" >> /opt/jetty/start.d/server.ini
RUN echo "org.slf4j.simpleLogger.log.org.eclipse.jetty.annotations.AnnotationParser=ERROR" >> /opt/jetty/start.d/server.ini

# Set defaults for docker run
WORKDIR /opt/jetty
EXPOSE 8080
CMD ["java", "-jar", "start.jar", "jetty.home=/opt/jetty"]
