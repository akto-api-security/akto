package com.akto.listener;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.DaoInit;
import com.akto.dao.AccountsDao;
import com.akto.dao.context.Context;
import com.akto.merging.Cron;
import com.akto.util.filter.DictionaryFilter;
import com.akto.utils.KafkaUtils;
import com.akto.utils.TagMismatchCron;
import com.mongodb.ConnectionString;


public class InitializerListener implements ServletContextListener {
    
    public static boolean connectedToMongo = false;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static final Logger logger = LoggerFactory.getLogger(InitializerListener.class);


    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        DictionaryFilter.readDictionaryBinary();

        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        String triggerMergingCron = System.getenv("TRIGGER_MERGING_CRON");

        executorService.schedule(new Runnable() {
            public void run() {
                boolean calledOnce = false;
                do {
                    try {
                        if (!calledOnce) {
                            DaoInit.init(new ConnectionString(mongoURI));
                            calledOnce = true;
                        }
                        checkMongoConnection();

                        if (triggerMergingCron != null && triggerMergingCron.equalsIgnoreCase("false")) {
                            logger.info("skipping triggering merging cron");
                        } else {
                            Cron cron = new Cron();
                            logger.info("triggering merging cron for db abstractor " + Context.now());
                            cron.cron(true);
                            TagMismatchCron tagsMismatchCron = new TagMismatchCron();
                            logger.info("triggering tags mismatch cron for db abstractor " + Context.now());
                            tagsMismatchCron.runCron();
                        }

                    } catch (Exception e) {
                        logger.error("error running initializer method for db abstractor", e);
                    } finally {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } while (!connectedToMongo);
            }
        }, 0, TimeUnit.SECONDS);

        KafkaUtils kafkaUtils = new KafkaUtils();
        logger.info("trying to init kafka consumer and producer");
        if (kafkaUtils.isWriteEnabled()) {
            logger.info("init kafka producer");
            kafkaUtils.initKafkaProducer();
        }

        if (kafkaUtils.isReadEnabled()) {
            logger.info("init kafka consumer");
            kafkaUtils.initKafkaConsumer();
        }

        // Start fast-discovery consumer in background thread (if enabled)
        if (kafkaUtils.isFastDiscoveryEnabled()) {
            logger.info("Starting fast-discovery consumer in background thread");
            kafkaUtils.startFastDiscoveryConsumer();
        }
    }

    private static void checkMongoConnection() throws Exception {
        AccountsDao.instance.getStats();
        connectedToMongo = true;
    }

    @Override
    public void contextDestroyed(javax.servlet.ServletContextEvent sce) {
        // override
    }

}
