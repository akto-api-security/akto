FROM amazoncorretto:8
WORKDIR /app
COPY ./target/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar /app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar

RUN yum update -y && yum install -y procps
RUN yum install -y bind-utils

COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh
CMD ["/app/start.sh"]