package com.akto.listener;

import javax.servlet.ServletContextListener;

import com.akto.config.GuardrailsConfig;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.log.LoggerMaker;
import com.akto.metrics.ModuleInfoWorker;
import com.akto.utils.KafkaUtils;
import com.akto.utils.TopicPublisher;


public class InitializerListener implements ServletContextListener {

    private static final LoggerMaker logger = new LoggerMaker(InitializerListener.class, LoggerMaker.LogDb.DATA_INGESTION);

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        // Initialize Kafka
        KafkaUtils kafkaUtils = new KafkaUtils();
        kafkaUtils.initKafkaProducer();

        // Initialize GuardrailsConfig and TopicPublisher
        GuardrailsConfig guardrailsConfig = GuardrailsConfig.getInstance();
        logger.infoAndAddToDb("Guardrails configuration: " + guardrailsConfig,
            LoggerMaker.LogDb.DATA_INGESTION);

        // Store publisher for use in KafkaUtils
        TopicPublisher topicPublisher = new TopicPublisher(
            KafkaUtils.getKafkaProducer(),
            guardrailsConfig
        );
        KafkaUtils.setTopicPublisher(topicPublisher);

        boolean tcpEnabled = isEnabled("SYSLOG_TCP_ENABLED", true);
        if (tcpEnabled) {
            Thread syslogTcpThread = new Thread(new SyslogTcpListener());
            syslogTcpThread.setDaemon(true);
            syslogTcpThread.setName("syslog-tcp-listener");
            syslogTcpThread.start();
            logger.infoAndAddToDb("Syslog TCP listener thread started", LoggerMaker.LogDb.DATA_INGESTION);
        } else {
            logger.infoAndAddToDb("Syslog TCP listener disabled via SYSLOG_TCP_ENABLED", LoggerMaker.LogDb.DATA_INGESTION);
        }

        // Initialize DataActor
        DataActor dataActor = DataActorFactory.fetchInstance();
        ModuleInfoWorker.init(ModuleInfo.ModuleType.DATA_INGESTION, dataActor);
    }

    @Override
    public void contextDestroyed(javax.servlet.ServletContextEvent sce) {
        // override
    }

    private boolean isEnabled(String envVar, boolean defaultValue) {
        String value = System.getenv(envVar);
        if (value == null) {
            return defaultValue;
        }
        value = value.trim().toLowerCase();
        if ("true".equals(value) || "1".equals(value) || "yes".equals(value) || "y".equals(value) || "on".equals(value)) {
            return true;
        }
        if ("false".equals(value) || "0".equals(value) || "no".equals(value) || "n".equals(value) || "off".equals(value)) {
            return false;
        }
        return defaultValue;
    }

}
