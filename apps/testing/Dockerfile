FROM amazoncorretto:8

WORKDIR /app

RUN yum update -y && yum install -y procps

COPY ./target/testing-1.0-SNAPSHOT-jar-with-dependencies.jar /app/testing-1.0-SNAPSHOT-jar-with-dependencies.jar
COPY start.sh /app/start.sh

RUN chmod +x /app/start.sh

CMD ["/app/start.sh"]
