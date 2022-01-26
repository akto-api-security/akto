FROM jetty:9.4-jre8
RUN apk add build-base
RUN apk add libpcap-dev

ADD ./target/dashboard.war /var/lib/jetty/webapps/root.war
EXPOSE 8080
