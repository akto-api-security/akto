package com.akto.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.context.Context;
import com.akto.dto.IngestDataBatch;
import com.akto.kafka.Kafka;
import com.mongodb.BasicDBObject;

public class KafkaUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaUtils.class);
    private static Kafka kafkaProducer;

    public void initKafkaProducer() {
        String kafkaBrokerUrl = System.getenv("AKTO_KAFKA_BROKER_URL");
        int batchSize = Integer.parseInt(System.getenv("AKTO_KAFKA_PRODUCER_BATCH_SIZE"));
        int kafkaLingerMS = Integer.parseInt(System.getenv("AKTO_KAFKA_PRODUCER_LINGER_MS"));
        kafkaProducer = new Kafka(kafkaBrokerUrl, kafkaLingerMS, batchSize);
        logger.info("Kafka Producer Init " + Context.now());
    }

    public static void insertData(IngestDataBatch payload) {
        String topicName = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        BasicDBObject obj = new BasicDBObject();
        obj.put("path", payload.getPath());
        obj.put("requestHeaders", payload.getRequestHeaders());
        obj.put("responseHeaders", payload.getResponseHeaders());
        obj.put("method", payload.getMethod());
        obj.put("requestPayload", payload.getRequestPayload());
        obj.put("responsePayload", payload.getResponsePayload());
        obj.put("ip", payload.getIp());
        obj.put("time", payload.getTime());
        obj.put("statusCode", payload.getStatusCode());
        obj.put("type", payload.getType());
        obj.put("status", payload.getStatus());
        obj.put("akto_account_id", payload.getAkto_account_id());
        obj.put("akto_vxlan_id", payload.getAkto_vxlan_id());
        obj.put("is_pending", payload.getIs_pending());
        obj.put("source", payload.getSource());
        kafkaProducer.send(obj.toString(), topicName);
    }

}