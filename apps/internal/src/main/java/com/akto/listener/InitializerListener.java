package com.akto.listener;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextListener;

import com.akto.DaoInit;
import com.akto.dao.AccountsDao;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.ConnectionString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InitializerListener implements ServletContextListener {
    public static boolean connectedToMongo = false;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static final LoggerMaker loggerMaker = new LoggerMaker(InitializerListener.class);
    private static final Logger logger = LoggerFactory.getLogger(InitializerListener.class);

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {

        String mongoURI = System.getenv("BILLING_DB_CONN_URL");
        logger.info("MONGO URI " + mongoURI);

        executorService.schedule(new Runnable() {
            public void run() {
                boolean calledOnce = false;
                do {
                    try {
                        if (!calledOnce) {
                            DaoInit.init(new ConnectionString(mongoURI));
                            calledOnce = true;
                        }
                        checkMongoConnection();
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(String.format("Error: %s", e.getMessage()), LogDb.BILLING);
                    } finally {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } while (!connectedToMongo);
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
