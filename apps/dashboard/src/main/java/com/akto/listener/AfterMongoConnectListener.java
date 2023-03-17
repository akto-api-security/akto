package com.akto.listener;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class AfterMongoConnectListener implements ServletContextListener {

    private boolean ranOnce = false;

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

                    try {
                        runMainFunction();
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
