FROM amazoncorretto:8
WORKDIR /app
COPY ./target/testing-1.0-SNAPSHOT-jar-with-dependencies.jar /app/testing-1.0-SNAPSHOT-jar-with-dependencies.jar
RUN yum install https://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm
RUN yum install libuv --disableplugin=priorities
RUN yum install nodejs npm --enablerepo=epel-testing
RUN node --version
RUN npm --version
RUN npm install puppeteer
RUN npm install @puppeteer/replay
CMD "java" "-XX:+ExitOnOutOfMemoryError" "-jar" "/app/testing-1.0-SNAPSHOT-jar-with-dependencies.jar"
