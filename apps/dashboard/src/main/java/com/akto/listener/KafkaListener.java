package com.akto.listener;


import com.akto.kafka.Kafka;

import javax.servlet.ServletContextListener;

public class KafkaListener implements ServletContextListener {
    public static Kafka kafka;
    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        String brokerIP = "kafka1:19092"; //System.getenv("AKTO_KAFKA_BROKER_URL");
        if (brokerIP != null) {
            try {
                kafka = new Kafka(brokerIP);
            } catch (Exception e) {
                System.out.println("********************************************************************************");
                System.out.println("ERROR while setting up KafkaListener");
                System.out.println("********************************************************************************");
            }
        }

    }
}
