FROM openjdk
WORKDIR /app

RUN yum update -y && yum install -y procps

COPY ./target/api-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar /app/api-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar
COPY start.sh /app/start.sh

RUN chmod +x /app/start.sh

CMD ["/app/start.sh"]