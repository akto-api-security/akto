package com.akto.hybrid_runtime;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.sql.SampleDataAltDb;

public class CleanPostgres {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CleanPostgres.class, LogDb.RUNTIME);
    final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void cleanPostgresCron() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                cleanPostgresJob();
            }
        }, 0, 15, TimeUnit.MINUTES);
    }

    private static void cleanPostgresJob() {

        try {
            SampleDataAltDb.deleteOld();
        } catch (Exception ex) {
            loggerMaker.errorAndAddToDb(ex,
                    String.format("Failed to delete from postgres %s", ex.getMessage()));
        }
    }

    public static void main(String[] args) {
        cleanPostgresCron();
    }

}