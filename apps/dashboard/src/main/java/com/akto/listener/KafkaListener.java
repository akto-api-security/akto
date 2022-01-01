package com.akto.listener;


import com.akto.kafka.Kafka;

import javax.servlet.ServletContextListener;

public class KafkaListener implements ServletContextListener {
    public static Kafka kafka;
    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        String brokerIP = System.getenv("AKTO_KAFKA_BROKER_URL");
//        String brokerIP = "172.18.0.4:9092";
        if (brokerIP != null) {
            kafka = new Kafka(brokerIP);
        }

    }
}
