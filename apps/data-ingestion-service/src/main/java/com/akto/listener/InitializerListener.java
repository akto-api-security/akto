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

        // Start Syslog UDP listener for Apigee async connector (Option B)
        // This enables receiving traffic data from Apigee MessageLogging via UDP Syslog
        // without requiring an external Fluentd service
        Thread syslogThread = new Thread(new SyslogUdpListener());
        syslogThread.setDaemon(true);
        syslogThread.setName("syslog-udp-listener");
        syslogThread.start();
        logger.infoAndAddToDb("Syslog UDP listener thread started", LoggerMaker.LogDb.DATA_INGESTION);

        // Initialize DataActor
        DataActor dataActor = DataActorFactory.fetchInstance();
        ModuleInfoWorker.init(ModuleInfo.ModuleType.DATA_INGESTION, dataActor);
    }

    @Override
    public void contextDestroyed(javax.servlet.ServletContextEvent sce) {
        // override
    }

}
