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


    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        try {
            logger.info("initiate create sample data table operation");
            com.akto.sql.Main.createSampleDataTable();
        } catch(Exception e){
            e.printStackTrace();
            logger.error("Error creating sample data table" + e.getMessage());
            System.exit(0);
        }

        try {
            logger.info("initiate create index operation");
            SampleDataAltDb.createIndex();
        } catch(Exception e){
            e.printStackTrace();
            logger.error("Error creating index" + e.getMessage());
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
