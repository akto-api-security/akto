package com.akto.listener;


import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.DashboardMode;


import javax.servlet.ServletContextListener;

public class KafkaListener implements ServletContextListener {
    public static Kafka kafka;
    public static final int BATCH_SIZE_CONFIG = 999900;
    private static final LoggerMaker loggerMaker = new LoggerMaker(KafkaListener.class, LogDb.DASHBOARD);
    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        String brokerIP = "kafka1:19092"; //System.getenv("AKTO_KAFKA_BROKER_URL");
        
        if (DashboardMode.isKubernetes()) {
            loggerMaker.debugAndAddToDb("is_kubernetes: true", LogDb.DASHBOARD);
            return;
        }

        if (DashboardMode.isLocalDeployment() && !DashboardMode.isSaasDeployment()) {
            loggerMaker.debugAndAddToDb("local: true", LogDb.DASHBOARD);
            return;
        }

        if (brokerIP != null) {
            try {
                kafka = new Kafka(brokerIP,  1000, BATCH_SIZE_CONFIG);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "ERROR while setting up KafkaListener", LogDb.DASHBOARD);
            }
        }

    }
}
