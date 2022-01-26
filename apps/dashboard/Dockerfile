FROM jetty:9.4-jre8
USER root
RUN apt-get update -y
RUN apt-get install -y --no-install-recommends libpcap-dev
ADD ./target/dashboard.war /var/lib/jetty/webapps/root.war
EXPOSE 8080
