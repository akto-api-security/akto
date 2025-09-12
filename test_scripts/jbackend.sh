

# export JAVA_TOOL_OPTIONS="-javaagent:/Users/shivanshagrawal/open_source/otel/opentelemetry-javaagent.jar"
# export OTEL_TRACES_EXPORTER=otlp
# export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
# export OTEL_METRICS_EXPORTER=none
# export OTEL_LOGS_EXPORTER=none
# export OTEL_RESOURCE_ATTRIBUTES=service.name=akto-dashboard
# export OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
# export HTTPS_PROXY_PORT=8082
# export HTTPS_PROXY_HOST=localhost
# export HTTP_PROXY_HOST=localhost
# export HTTP_PROXY_PORT=8082
# export PROXY_URI=localhost:8082

export OLLAMA_SERVER_ENDPOINT=http://35.226.83.20/api/generate
# export AKTO_KAFKA_TOPIC_NAME=akto.api.logs
export AKTO_KAFKA_BROKER_URL=localhost:19092
export AKTO_KAFKA_BROKER_MAL=localhost:29092
export AKTO_KAFKA_GROUP_ID_CONFIG=asdf
export AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG=100
export AKTO_ACCOUNT_NAME=Helios
export AKTO_TRAFFIC_BATCH_SIZE=100
export AKTO_TRAFFIC_BATCH_TIME_SECS=10
export AKTO_INSTANCE_TYPE=DASHBOARD
export USE_PROXY=true
export AKTO_LOG_LEVEL=DEBUG
# export PROXY_URI=http://userShivansh:password123Shivansh@18.142.179.219:8888
# export NO_PROXY=127.0.0.1

export THREAT_DETECTION_BACKEND_URL=http://localhost:9090

# Force index creation and job functions to run
export AKTO_RUN_JOB=true




# export NODE_ENV=development
export IS_SAAS=true
export DASHBOARD_MODE=LOCAL_DEPLOY
# export DASHBOARD_MODE=ON_PREM
export AWS_REGION=ap-south-1
# export USAGE_SERVICE_URL=http://localhost:9000
# export USAGE_SERVICE_URL=http://ac10ab3af15934fb7bb07135b612cc61-6e26e3000762c90c.elb.ap-south-1.amazonaws.com:8080
export AKTO_MONGO_CONN="mongodb://localhost:27017"
export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8087, -Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.port=9013 -Dcom.sun.management.jmxremote.rmi.port=9013 -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dorg.slf4j.simpleLogger.log.org.eclipse.jetty.annotations.AnnotationParser=ERROR"
export PUPPETEER_REPLAY_SERVICE_URL=http://localhost:3000
echo "\n"
echo "\x1B[1;31m$PWD \033[0m"
echo "\x1B[1;31mIS_SAAS:\x1B[0m \x1B[1;32m $IS_SAAS\033[0m"
echo "\x1B[1;31mDASHBOARD_MODE:\x1B[0m \x1B[1;32m $DASHBOARD_MODE\033[0m"
echo "\x1B[1;31mAKTO_MONGO_CONN:\x1B[0m \x1B[1;32m $AKTO_MONGO_CONN\033[0m"
echo "\n"

mvn --projects :dashboard --also-make jetty:run 
