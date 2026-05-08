package com.akto.listener;

import javax.servlet.ServletContextListener;

import com.akto.DaoInit;
import com.akto.config.GuardrailsConfig;
import com.akto.dao.AccountsDao;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.log.LoggerMaker;
import com.akto.metrics.ModuleInfoWorker;
import com.akto.utils.KafkaUtils;
import com.akto.utils.McpCollectionResolver;
import com.akto.utils.TopicPublisher;
import com.mongodb.ConnectionString;


public class InitializerListener implements ServletContextListener {

    private static final LoggerMaker logger = new LoggerMaker(InitializerListener.class, LoggerMaker.LogDb.DATA_INGESTION);

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        initMongoConnection();

        // Initialize Kafka
        KafkaUtils kafkaUtils = new KafkaUtils();
        kafkaUtils.initKafkaProducer();

        // Initialize GuardrailsConfig and TopicPublisher
        GuardrailsConfig guardrailsConfig = GuardrailsConfig.getInstance();
        logger.infoAndAddToDb("Guardrails configuration: " + guardrailsConfig);

        // Store publisher for use in KafkaUtils
        TopicPublisher topicPublisher = new TopicPublisher(
            KafkaUtils.getKafkaProducer(),
            guardrailsConfig
        );
        KafkaUtils.setTopicPublisher(topicPublisher);

        String tcpEnv = System.getenv("SYSLOG_TCP_ENABLED");
        boolean tcpEnabled = tcpEnv == null || Boolean.parseBoolean(tcpEnv.trim());
        if (tcpEnabled) {
            Thread syslogTcpThread = new Thread(new SyslogTcpListener());
            syslogTcpThread.setDaemon(true);
            syslogTcpThread.setName("syslog-tcp-listener");
            syslogTcpThread.start();
            logger.infoAndAddToDb("Syslog TCP listener thread started");
        } else {
            logger.infoAndAddToDb("Syslog TCP listener disabled via SYSLOG_TCP_ENABLED");
        }

        // Initialize DataActor
        DataActor dataActor = DataActorFactory.fetchInstance();
        ModuleInfoWorker.init(ModuleInfo.ModuleType.DATA_INGESTION, dataActor);

        // Warm the MCP collection-name cache and start the periodic refresher
        McpCollectionResolver.getInstance().start();
    }

    private void initMongoConnection() {
        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        if (mongoURI == null || mongoURI.trim().isEmpty()) {
            logger.errorAndAddToDb(new IllegalArgumentException("AKTO_MONGO_CONN is missing"),
                    "AKTO_MONGO_CONN is not set. Mongo-backed auth/config lookups may fail");
            return;
        }

        try {
            DaoInit.init(new ConnectionString(mongoURI));
            AccountsDao.instance.getStats();
            logger.infoAndAddToDb("Mongo connection initialized for data-ingestion-service");
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Failed to initialize Mongo for data-ingestion-service");
        }
    }

    @Override
    public void contextDestroyed(javax.servlet.ServletContextEvent sce) {
        // override
    }

}
