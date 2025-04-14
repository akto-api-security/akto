package com.akto.listener;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.jobs.JobUtils;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public abstract class AfterMongoConnectListener implements ServletContextListener {

    private boolean ranOnce = false;
    private static final LoggerMaker logger = new LoggerMaker(AfterMongoConnectListener.class, LogDb.DASHBOARD);

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public abstract void runMainFunction() ;
    public abstract int retryAfter();
    @Override
    public void contextInitialized(ServletContextEvent sce) {

        executorService.schedule( new Runnable() {
            public void run() {
                while (!ranOnce) {
                    if (!InitializerListener.connectedToMongo) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        continue;
                    }

                    boolean runJobFunctions = JobUtils.getRunJobFunctions();
                    boolean runJobFunctionsAnyway = JobUtils.getRunJobFunctionsAnyway();

                    try {

                        int now = Context.now();
                        if (runJobFunctions || runJobFunctionsAnyway) {
                            logger.debug("Starting runtime init functions at " + now);
                            runMainFunction();
                            int now2 = Context.now();
                            int diffNow = now2 - now;
                            logger.debug(String.format(
                                    "Completed runtime init functions at %d , time taken : %d", now2,
                                    diffNow));
                        } else {
                            logger.debug("Skipping runtime init functions at " + now);
                        }
    
                        ranOnce = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (!ranOnce) { // when runMainFunction errors out
                        if (retryAfter() == -1) {
                            ranOnce = true;
                        } else {
                            try {
                                Thread.sleep(1000L *(retryAfter()));
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                }
            }
        }, 0 , TimeUnit.SECONDS);

    }

}
