package com.akto.crons;

import com.akto.sql.SampleDataAltDb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CleanPostgres {

    final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
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

}
