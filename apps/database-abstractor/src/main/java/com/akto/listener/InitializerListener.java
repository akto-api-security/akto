package com.akto.listener;

import com.akto.DaoInit;
import com.akto.dao.AccountsDao;
import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.merging.Cron;
import com.akto.utils.KafkaUtils;
import com.mongodb.ConnectionString;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletContextListener;


public class InitializerListener implements ServletContextListener {
    
    public static boolean connectedToMongo = false;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static final LoggerMaker logger = new LoggerMaker(InitializerListener.class, LogDb.DB_ABS);


    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {

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
                            logger.info("triggering merging cron for db abstractor {}", Context.now());
                            cron.cron(true);
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
