package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.akto.action.IngestionAction;
import com.akto.dao.context.Context;
import com.akto.dto.IngestDataBatch;
import com.akto.kafka.Kafka;
import com.mongodb.BasicDBObject;

public class KafkaUtils {

    private static final LoggerMaker logger = new LoggerMaker(KafkaUtils.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static Kafka kafkaProducer;

    public void initKafkaProducer() {
        String kafkaBrokerUrl = System.getenv().getOrDefault("AKTO_KAFKA_BROKER_URL", "localhost:29092");
        int batchSize = Integer.parseInt(System.getenv().getOrDefault("AKTO_KAFKA_PRODUCER_BATCH_SIZE", "100"));
        int kafkaLingerMS = Integer.parseInt(System.getenv().getOrDefault("AKTO_KAFKA_PRODUCER_LINGER_MS", "10"));
        kafkaProducer = new Kafka(kafkaBrokerUrl, kafkaLingerMS, batchSize, LoggerMaker.LogDb.DATA_INGESTION);
        logger.infoAndAddToDb("Kafka Producer Init " + Context.now(), LoggerMaker.LogDb.DATA_INGESTION);
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

        kafkaProducer.send(obj.toString(), topicName);
        IngestionAction.printLogs("Inserted to kafka: " + obj.toString());
    }

}