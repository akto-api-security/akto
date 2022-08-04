package com.akto.listener;


import com.akto.kafka.Kafka;

import javax.servlet.ServletContextListener;

public class KafkaListener implements ServletContextListener {
    public static Kafka kafka;
    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        String brokerIP = "kafka1:19092"; //System.getenv("AKTO_KAFKA_BROKER_URL");
        if (brokerIP != null) {
            kafka = new Kafka(brokerIP);
        }

    }
}
