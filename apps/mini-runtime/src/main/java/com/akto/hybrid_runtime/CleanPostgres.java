package com.akto.hybrid_runtime;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.akto.dao.common.LoggerMaker;
import com.akto.dao.common.LoggerMaker.LogDb;

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

    }

}