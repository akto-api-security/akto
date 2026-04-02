FROM amazoncorretto:8
WORKDIR /app
COPY ./target/datadog-forwarder-1.0-SNAPSHOT-jar-with-dependencies.jar /app/datadog-forwarder-1.0-SNAPSHOT-jar-with-dependencies.jar

RUN yum update -y && yum install -y procps
RUN yum install -y bind-utils

COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh
CMD ["/app/start.sh"]
