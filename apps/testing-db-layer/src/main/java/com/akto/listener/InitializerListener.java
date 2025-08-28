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
    private static final boolean DELETE_CRONS_ENABLED = System.getenv().getOrDefault("DELETE_CRONS_ENABLED", "false").equals("true");
    private static final boolean TOP_SAMPLES_TRIGGER_ENABLED = System.getenv().getOrDefault("TOP_SAMPLES_TRIGGER_ENABLED", "true").equals("true");
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

        createTop10Trigger();
        setupDeleteCrons();

        // todo: add metrics on all the postgres operations

    }

    private void setupDeleteCrons() {

        if (!DELETE_CRONS_ENABLED) {
            logger.info("DELETE_CRONS_ENABLED is false, skipping clean postgres cron");
            return;
        }

        try {
            CleanPostgres.cleanPostgresCron();
        } catch (Exception e) {
            logger.error("Unable to clean postgres", e);
        }

        try {
            CleanPostgres.cleanApiCollectionZeroDataJobCron();
        } catch (Exception e) {
            logger.error("Error scheduling cleanApiCollectionZeroDataJob job ", e);
        }

        try {
            CleanPostgres.deleteOldTimestampInBatchJobCron();
        } catch (Exception e) {
            logger.error("Error scheduling deleteOldTimestampInBatchJobCron job ", e);
        }
    }

    private void createTop10Trigger() {
        if (!TOP_SAMPLES_TRIGGER_ENABLED) {
            logger.info("TOP_SAMPLES_TRIGGER_ENABLED is false, skipping create top 10 trigger");
            if (!DELETE_CRONS_ENABLED) {
                logger.error("Delete crons must be enabled if top 10 trigger is false");
                System.exit(1);
            }
            return;
        }

        for (int i = 1; i <= MAX_RETRIES; i++) {
            try {
                logger.info("initiate create top 10 trigger operation");
                SampleDataAltDb.createTop10Trigger();
                break;
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error creating top 10 trigger" + e.getMessage());
                try {
                    logger.info("sleeping during create top 10 trigger operation " + SLEEP_DURATION * i
                            + " retry attempt " + i);
                    if (i < MAX_RETRIES) {
                        Thread.sleep(SLEEP_DURATION * i);
                    }
                } catch (Exception ex) {
                    logger.error(
                            "error initiating sleep operation for create top 10 trigger operation " + ex.getMessage());
                }
            }
        }

    }

    @Override
    public void contextDestroyed(javax.servlet.ServletContextEvent sce) {
        // override
    }

}
