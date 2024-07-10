package com.akto.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.context.Context;
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

    public static void insertData(String path, String requestHeaders, String responseHeaders, String method, 
    String requestPayload, String responsePayload, String ip, String time, String statusCode, String type,
    String status, String akto_account_id, String akto_vxlan_id, String is_pending, String source) {
        String topicName = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        BasicDBObject obj = new BasicDBObject();
        obj.put("path", path);
        obj.put("requestHeaders", requestHeaders);
        obj.put("responseHeaders", responseHeaders);
        obj.put("method", method);
        obj.put("requestPayload", requestPayload);
        obj.put("responsePayload", responsePayload);
        obj.put("ip", ip);
        obj.put("time", time);
        obj.put("statusCode", statusCode);
        obj.put("type", type);
        obj.put("status", status);
        obj.put("akto_account_id", akto_account_id);
        obj.put("akto_vxlan_id", akto_vxlan_id);
        obj.put("is_pending", is_pending);
        obj.put("source", source);
        kafkaProducer.send(obj.toString(), topicName);
    }

}