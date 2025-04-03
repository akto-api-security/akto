package com.akto.listener;

import javax.servlet.ServletContextListener;

import com.akto.crons.CleanPostgres;
import com.akto.sql.SampleDataAltDb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InitializerListener implements ServletContextListener {

    public static boolean connectedToMongo = false;
//    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static final Logger logger = LoggerFactory.getLogger(InitializerListener.class);
    int MAX_RETRIES = 10;
    int SLEEP_DURATION = 2000;

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {

        for (int i = 1; i <= MAX_RETRIES; i++) {
            try {
                logger.info("initiate create sample data table operation");
                com.akto.sql.Main.createSampleDataTable();
                break;
            } catch(Exception e){
                e.printStackTrace();
                logger.error("Error creating sample data table" + e.getMessage());
                try {
                    logger.info("sleeping during create table operation " + SLEEP_DURATION * i + " retry attempt " + i);
                    if (i < MAX_RETRIES) {
                        Thread.sleep(SLEEP_DURATION * i);
                    }
                } catch (Exception ex) {
                    logger.error("error initiating sleep operation for create table operation " + ex.getMessage());
                }
            }
        }

        for (int i = 1; i <= MAX_RETRIES; i++) {
            try {
                logger.info("initiate create index operation");
                SampleDataAltDb.createIndex();
                break;
            } catch(Exception e){
                e.printStackTrace();
                logger.error("Error creating index" + e.getMessage());
                try {
                    logger.info("sleeping during create index operation " + SLEEP_DURATION * i + " retry attempt " + i);
                    if (i < MAX_RETRIES) {
                        Thread.sleep(SLEEP_DURATION * i);
                    }
                } catch (Exception ex) {
                    logger.error("error initiating sleep operation for create index operation " + ex.getMessage());
                }
            }
        }

        // todo: add metrics on all the postgres operations
        try {
            CleanPostgres.cleanPostgresCron();
        } catch(Exception e){
            logger.error("Unable to clean postgres", e);
        }

    }

    @Override
    public void contextDestroyed(javax.servlet.ServletContextEvent sce) {
        // override
    }

}
