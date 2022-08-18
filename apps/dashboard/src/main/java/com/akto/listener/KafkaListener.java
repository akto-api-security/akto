package com.akto.listener;


import com.akto.kafka.Kafka;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextListener;

public class KafkaListener implements ServletContextListener {
    public static Kafka kafka;
    public static final int BATCH_SIZE_CONFIG = 999900;
    private static final Logger logger = LoggerFactory.getLogger(KafkaListener.class);
    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        String brokerIP = "kafka1:19092"; //System.getenv("AKTO_KAFKA_BROKER_URL");
        if (brokerIP != null) {
            try {
                kafka = new Kafka(brokerIP,  1000, BATCH_SIZE_CONFIG);
            } catch (Exception e) {
                logger.error("ERROR while setting up KafkaListener");
            }
        }

    }
}
