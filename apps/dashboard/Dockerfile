FROM jetty:9.4-jre8
USER root
RUN apt-get update -y
RUN apt-get install -y --no-install-recommends libpcap-dev
ADD ./target/dashboard.war /var/lib/jetty/webapps/root.war
RUN echo "--module=http-forwarded" > /var/lib/jetty/start.d/http-forwarded.ini
ENV NODE_VERSION=16.13.0
RUN apt install -y curl
RUN curl -fsSL https://deb.nodesource.com/setup_16.x | bash
RUN apt-get install nodejs
RUN node --version
RUN npm --version
RUN npm install puppeteer -g --unsafe-perm=true --allow-root
RUN npm install @puppeteer/replay
EXPOSE 8080
