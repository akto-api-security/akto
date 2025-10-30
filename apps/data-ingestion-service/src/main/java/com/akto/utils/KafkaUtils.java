package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.akto.dao.context.Context;
import com.akto.dto.IngestDataBatch;
import com.akto.kafka.Kafka;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.Serializer;
import com.akto.kafka.KafkaProtoProducer;
import com.akto.kafka.KafkaProducerConfig;
import com.mongodb.BasicDBObject;
import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.akto.proto.http_response_param.v1.StringList;
import java.util.HashMap;
import java.util.Map;

public class KafkaUtils {

    private static final LoggerMaker logger = new LoggerMaker(KafkaUtils.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static Kafka kafkaProducer;
    private static KafkaProtoProducer kafkaProtoProducer;

    public void initKafkaProducer() {
        String kafkaBrokerUrl = System.getenv().getOrDefault("AKTO_KAFKA_BROKER_URL", "localhost:29092");
        int batchSize = Integer.parseInt(System.getenv().getOrDefault("AKTO_KAFKA_PRODUCER_BATCH_SIZE", "100"));
        int kafkaLingerMS = Integer.parseInt(System.getenv().getOrDefault("AKTO_KAFKA_PRODUCER_LINGER_MS", "10"));
        kafkaProducer = new Kafka(kafkaBrokerUrl, kafkaLingerMS, batchSize, LoggerMaker.LogDb.DATA_INGESTION);

        // Initialize protobuf kafka producer
        KafkaConfig protoKafkaConfig = KafkaConfig.newBuilder()
            .setBootstrapServers(kafkaBrokerUrl)
            .setKeySerializer(Serializer.STRING)
            .setValueSerializer(Serializer.BYTE_ARRAY)
            .setProducerConfig(KafkaProducerConfig.newBuilder()
                .setLingerMs(kafkaLingerMS)
                .setBatchSize(batchSize)
                .build())
            .build();
        kafkaProtoProducer = new KafkaProtoProducer(protoKafkaConfig);

        logger.infoAndAddToDb("Kafka Producer Init " + Context.now());
    }

    public static boolean shouldSendToThreatTopic(String requestHeaders){

        if (requestHeaders == null || requestHeaders.isEmpty()) {
            return false;
        }

        String lowerHeaders = requestHeaders.toLowerCase();
        if (lowerHeaders.contains("\"host\":") || lowerHeaders.contains("\"host \":")) {
            if (lowerHeaders.contains("hollywoodbets") ||
                lowerHeaders.contains("betsolutions") ||
                lowerHeaders.contains("betnix") ||
                lowerHeaders.contains("betsoft")) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, StringList> parseHeadersToProto(String headersJson) {
        Map<String, StringList> headers = new HashMap<>();
        if (headersJson == null || headersJson.isEmpty()) {
            return headers;
        }

        try {
            // Parse JSON string to extract headers
            headersJson = headersJson.trim();
            if (headersJson.startsWith("{") && headersJson.endsWith("}")) {
                headersJson = headersJson.substring(1, headersJson.length() - 1);
                String[] pairs = headersJson.split(",");

                for (String pair : pairs) {
                    String[] keyValue = pair.split(":", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim().replaceAll("\"", "");
                        String value = keyValue[1].trim().replaceAll("\"", "");
                        headers.put(key, StringList.newBuilder().addValues(value).build());
                    }
                }
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Error parsing headers: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
        }

        return headers;
    }

    public static void insertData(IngestDataBatch payload) {
        String topicName = "akto.api.logs";
        BasicDBObject obj = new BasicDBObject();
        obj.put("path", payload.getPath());
        obj.put("requestHeaders", payload.getRequestHeaders());
        obj.put("responseHeaders", payload.getResponseHeaders());
        obj.put("method", payload.getMethod());
        obj.put("requestPayload", payload.getRequestPayload());
        obj.put("responsePayload", payload.getResponsePayload());
        obj.put("ip", payload.getIp());
        obj.put("destIp", payload.getDestIp());
        obj.put("time", payload.getTime());
        obj.put("statusCode", payload.getStatusCode());
        obj.put("type", payload.getType());
        obj.put("status", payload.getStatus());
        obj.put("akto_account_id", payload.getAkto_account_id());
        obj.put("akto_vxlan_id", payload.getAkto_vxlan_id());
        obj.put("is_pending", payload.getIs_pending());
        obj.put("source", payload.getSource());
        obj.put("direction", payload.getDirection());
        obj.put("process_id", payload.getProcess_id());
        obj.put("socket_id", payload.getSocket_id());
        obj.put("daemonset_id", payload.getDaemonset_id());
        obj.put("enabled_graph", payload.getEnabled_graph());
        obj.put("tag", payload.getTag());
        kafkaProducer.send(obj.toString(), "akto.api.logs");
        //IngestionAction.printLogs("Inserted to kafka: " + obj.toString());

        if(shouldSendToThreatTopic(payload.getRequestHeaders())){
            // create a HttpResponseParam protobuf object from payload send to akto.api.logs2 topic
            HttpResponseParam.Builder builder = HttpResponseParam.newBuilder();

            // Parse headers to protobuf format
            Map<String, StringList> requestHeaders = parseHeadersToProto(payload.getRequestHeaders());
            Map<String, StringList> responseHeaders = parseHeadersToProto(payload.getResponseHeaders());

            builder.setMethod(payload.getMethod() != null ? payload.getMethod() : "")
                .setPath(payload.getPath() != null ? payload.getPath() : "")
                .setType(payload.getType() != null ? payload.getType() : "HTTP/1.1")
                .putAllRequestHeaders(requestHeaders)
                .putAllResponseHeaders(responseHeaders)
                .setRequestPayload(payload.getRequestPayload() != null ? payload.getRequestPayload() : "")
                .setResponsePayload(payload.getResponsePayload() != null ? payload.getResponsePayload() : "")
                .setStatusCode(payload.getStatusCode() != null ? Integer.parseInt(payload.getStatusCode()) : 0)
                .setStatus(payload.getStatus() != null ? payload.getStatus() : "")
                .setTime(payload.getTime() != null ? Integer.parseInt(payload.getTime()) : (int)(System.currentTimeMillis() / 1000))
                .setAktoAccountId(payload.getAkto_account_id() != null ? payload.getAkto_account_id() : "")
                .setAktoVxlanId(payload.getAkto_vxlan_id() != null ? payload.getAkto_vxlan_id() : "")
                .setIp(payload.getIp() != null ? payload.getIp() : "")
                .setDestIp(payload.getDestIp() != null ? payload.getDestIp() : "")
                .setDirection(payload.getDirection() != null ? payload.getDirection() : "")
                .setIsPending(payload.getIs_pending() != null ? Boolean.parseBoolean(payload.getIs_pending()) : false)
                .setSource(payload.getSource() != null ? payload.getSource() : "");

            HttpResponseParam httpResponseParam = builder.build();
            kafkaProtoProducer.send("akto.api.logs2", httpResponseParam);
        }
    }

}