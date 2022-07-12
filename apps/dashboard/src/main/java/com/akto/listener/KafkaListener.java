package com.akto.listener;


import com.akto.kafka.Kafka;

import javax.servlet.ServletContextListener;

public class KafkaListener implements ServletContextListener {
    public static Kafka kafka;
    public static final int BATCH_SIZE_CONFIG = 999900;
    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        String brokerIP = System.getenv("AKTO_KAFKA_BROKER_URL");
        if (brokerIP != null) {
            kafka = new Kafka(brokerIP,  1000, BATCH_SIZE_CONFIG);
        }

    }
}
