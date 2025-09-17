package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.akto.dao.context.Context;
import com.akto.dto.IngestDataBatch;
import com.akto.kafka.Kafka;
import com.akto.kafka.Serializer;
import com.mongodb.BasicDBObject;
import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.akto.proto.http_response_param.v1.StringList;

import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

public class KafkaUtils {

    private static final LoggerMaker logger = new LoggerMaker(KafkaUtils.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static Kafka kafkaProducer;
    private static KafkaProducer<String, byte[]> kafkaThreatProducer;
    private static final boolean isThreatPushEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("AKTO_DI_KAFKA_THREAT_PUSH_ENABLED", "false"));

    public void initKafkaProducer() {
        String kafkaBrokerUrl = System.getenv().getOrDefault("AKTO_KAFKA_BROKER_URL", "localhost:29092");
        int batchSize = Integer.parseInt(System.getenv().getOrDefault("AKTO_KAFKA_PRODUCER_BATCH_SIZE", "100"));
        int kafkaLingerMS = Integer.parseInt(System.getenv().getOrDefault("AKTO_KAFKA_PRODUCER_LINGER_MS", "10"));
        kafkaProducer = new Kafka(kafkaBrokerUrl, kafkaLingerMS, batchSize, LoggerMaker.LogDb.DATA_INGESTION);
        logger.infoAndAddToDb("Kafka Producer Init " + Context.now(), LoggerMaker.LogDb.DATA_INGESTION);

        if(isThreatPushEnabled){
            int requestTimeoutMs = 5000;
            int lingerMs = 100;
            Properties kafkaProps = new Properties();
            kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokerUrl);
            kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serializer.STRING.getSerializer());
            kafkaProps.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, Serializer.BYTE_ARRAY.getSerializer());
            kafkaProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
            kafkaProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
            kafkaProps.put(ProducerConfig.RETRIES_CONFIG, 0);
            kafkaProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
            kafkaProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, lingerMs+ requestTimeoutMs);
            kafkaProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
            kafkaProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
            kafkaThreatProducer = new KafkaProducer<>(kafkaProps);
        }
    }

    public static void insertThreatData(IngestDataBatch payload){
        if(!isThreatPushEnabled) {
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();

        // Parse the request and response headers as JSON dumped strings
        Map<String, String> requestHeadersMap = null;
        Map<String, String> responseHeadersMap = null;
        Map<String, StringList> requestHeadersStringList = null;
        Map<String, StringList> responseHeadersStringList = null;
        try {
            requestHeadersMap = objectMapper.readValue(payload.getRequestHeaders(), Map.class);
            responseHeadersMap = objectMapper.readValue(payload.getResponseHeaders(), Map.class);

            // Convert keys to lowercase
            requestHeadersMap = requestHeadersMap.entrySet().stream()
                    .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));

            responseHeadersMap = responseHeadersMap.entrySet().stream()
                    .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));

            // Convert maps to String, StringList
            requestHeadersStringList = requestHeadersMap.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> 
                            StringList.newBuilder().addValues(entry.getValue()).build()));

            responseHeadersStringList = responseHeadersMap.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> 
                            StringList.newBuilder().addValues(entry.getValue()).build()));

        } catch (Exception e) {
            logger.errorAndAddToDb("Error parsing headers for threat: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
        }

        // convert IngestDataBatch to HttpResponseParam
        HttpResponseParam httpResponseParam = HttpResponseParam.newBuilder()
                .setMethod(payload.getMethod())
                .setPath(payload.getPath())
                .putAllRequestHeaders(requestHeadersStringList)
                .putAllResponseHeaders(responseHeadersStringList)
                .setRequestPayload(payload.getRequestPayload())
                .setResponsePayload(payload.getResponsePayload())
                .setIp(payload.getIp())
                .setDestIp(payload.getDestIp())
                .setTime(Integer.parseInt(payload.getTime()))
                .setStatusCode(Integer.parseInt(payload.getStatusCode()))
                .setType(payload.getType())
                .setStatus(payload.getStatus())
                .setAktoAccountId(payload.getAkto_account_id())
                .setAktoVxlanId(payload.getAkto_vxlan_id())
                .setIsPending(Boolean.parseBoolean(payload.getIs_pending()))
                .build();
        String topicName = System.getenv().getOrDefault("AKTO_KAFKA_THREAT_TOPIC_NAME", "akto.api.logs2");
        kafkaThreatProducer.send(new ProducerRecord<String,byte[]>(topicName, httpResponseParam.toByteArray()));
    }

    public static void insertData(IngestDataBatch payload) {
        String topicName = System.getenv().getOrDefault("AKTO_KAFKA_TOPIC_NAME", "akto.api.logs");
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
        kafkaProducer.send(obj.toString(), topicName);

        insertThreatData(payload);
    }

}