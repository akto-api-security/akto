package com.akto.crons;

import com.akto.dao.context.Context;
import com.akto.sql.SampleDataAltDb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CleanPostgres {

    final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private static final Logger logger = LoggerFactory.getLogger(CleanPostgres.class);

    public static void cleanPostgresCron() {

        String timeStr = System.getenv("PG_CLEAN_INTERVAL");
        int time = 8;
        try {
            time = Integer.parseInt(timeStr);
        } catch (Exception e) {
        }

        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                logger.info("triggering old data deletion cron");
                cleanPostgresJob();
            }   
        }, 0, time, TimeUnit.MINUTES);
    }

    private static void cleanPostgresJob() {
        try {
            int deleteCount = SampleDataAltDb.deleteOld();
            logger.info("old entries deletion count "  + deleteCount);
        } catch (Exception e) {
            logger.error("Failed to delete from postgres" + e.getMessage());
        }
    }

    public static void cleanApiCollectionZeroDataJobCron() {

        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                logger.info("triggering cleanApiCollectionZeroDataJobCron");
                cleanApiCollectionZeroDataJob();
            }   
        }, 0, 30, TimeUnit.SECONDS);
    }

    public static void cleanApiCollectionZeroDataJob() {
        try {
            logger.info("cleanApiCollectionZeroDataJob started " + Context.now());
            int deleteCount = SampleDataAltDb.deleteApiCollectionZeroEntriesInBatch();
            logger.info("cleanApiCollectionZeroDataJob iteration deletion count "  + deleteCount + " ts " + Context.now());
        } catch (Exception e) {
            logger.error("cleanApiCollectionZeroDataJob failed with error " + e.getMessage());
        }
    }

    public static void deleteOldTimestampInBatchJobCron() {

        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                logger.info("triggering deleteOldTimestampInBatchJobCron");
                deleteOldTimestampInBatchJob();
            }   
        }, 0, 1, TimeUnit.MINUTES);
    }

    public static void deleteOldTimestampInBatchJob() {
        try {
            logger.info("deleteOldTimestampInBatchJob started " + Context.now());
            int deleteCount = SampleDataAltDb.deleteOldTimestampInBatch();
            logger.info("deleteOldTimestampInBatchJob iteration deletion count "  + deleteCount + " ts " + Context.now());
        } catch (Exception e) {
            logger.error("deleteOldTimestampInBatchJob failed with error " + e.getMessage());
        }
    }

}
