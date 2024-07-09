package com.akto.hybrid_runtime;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.sql.SampleDataAltDb;

public class CleanPostgres {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CleanPostgres.class, LogDb.RUNTIME);
    final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void cleanPostgresCron() {

        String timeStr = System.getenv("PG_CLEAN_INTERVAL");
        int time = 8;
        try {
            time = Integer.parseInt(timeStr);
        } catch (Exception e) {
        }

        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                long start = System.currentTimeMillis();
                cleanPostgresJob();
                AllMetrics.instance.setStaleSampleDataCleanupJobLatency(System.currentTimeMillis() - start);
            }
        }, 0, time, TimeUnit.MINUTES);
    }

    private static void cleanPostgresJob() {

        try {
            int deleteCount = SampleDataAltDb.deleteOld();
            AllMetrics.instance.setStaleSampleDataDeletedCount(deleteCount);
        } catch (Exception ex) {
            loggerMaker.errorAndAddToDb(ex,
                    String.format("Failed to delete from postgres %s", ex.getMessage()));
        }
    }

    public static void main(String[] args) {
        cleanPostgresCron();
    }

}