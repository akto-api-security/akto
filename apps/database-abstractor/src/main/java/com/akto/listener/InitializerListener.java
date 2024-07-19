package com.akto.listener;


import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.DaoInit;
import com.akto.dao.AccountsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SensitiveSampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.merging.Cron;
import com.akto.scripts.InsertRecordsInKafka;
import com.akto.util.AccountTask;
import com.mongodb.ConnectionString;


public class InitializerListener implements ServletContextListener {
    
    public static boolean connectedToMongo = false;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static final Logger logger = LoggerFactory.getLogger(InitializerListener.class);

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        String kafkaUrl = "10.100.131.197:9092";
        executorService.schedule(new Runnable() {
            public void run() {
                try {
                    for (int i = 0; i < 500_000; i+=10) {
                        logger.info("starting kafka insert " + i);
                        InsertRecordsInKafka.insertRandomRecords(i, kafkaUrl);
                    }
                    logger.info("finished kafka insert");
                } catch (Exception e) {
                    logger.error("error in inserting records in kafka " + e.getMessage());
                }
            }
        }, 0, TimeUnit.SECONDS);
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
