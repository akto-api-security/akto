FROM amazoncorretto:8
WORKDIR /app
COPY ./target/testing-1.0-SNAPSHOT-jar-with-dependencies.jar /app/testing-1.0-SNAPSHOT-jar-with-dependencies.jar
ENV NODE_VERSION=16.13.0
RUN apt install -y curl
RUN curl -fsSL https://deb.nodesource.com/setup_16.x | bash
RUN apt-get install nodejs
RUN node --version
RUN npm --version
RUN npm install puppeteer
RUN npm install @puppeteer/replay
CMD "java" "-XX:+ExitOnOutOfMemoryError" "-jar" "/app/testing-1.0-SNAPSHOT-jar-with-dependencies.jar"
