package com.akto.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.servlet.ServletContextListener;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.DaoInit;
import com.akto.dao.AccountsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.SampleData;
import com.akto.log.LoggerMaker;
import com.akto.merging.Cron;
import com.akto.util.AccountTask;
import com.akto.util.UsageUtils;
import com.akto.utils.KafkaUtils;
import com.akto.utils.RedactSampleData;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;


public class InitializerListener implements ServletContextListener {
    
    public static boolean connectedToMongo = false;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final Logger logger = LoggerFactory.getLogger(InitializerListener.class);

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {

        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        String triggerMergingCron = System.getenv("TRIGGER_MERGING_CRON");

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

                        if (triggerMergingCron != null && triggerMergingCron.equalsIgnoreCase("false")) {
                            logger.info("skipping triggering merging cron");
                        } else {
                            Cron cron = new Cron();
                            logger.info("triggering merging cron for db abstractor " + Context.now());
                            cron.cron(true);
                        }

                    } catch (Exception e) {
                        logger.error("error running initializer method for db abstractor", e);
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

        KafkaUtils kafkaUtils = new KafkaUtils();
        logger.info("trying to init kafka consumer and producer");
        if (kafkaUtils.isWriteEnabled()) {
            logger.info("init kafka producer");
            kafkaUtils.initKafkaProducer();
        }

        if (kafkaUtils.isReadEnabled()) {
            logger.info("init kafka consumer");
            kafkaUtils.initKafkaConsumer();
        }

        redactJob();
    }

    private static void checkMongoConnection() throws Exception {
        AccountsDao.instance.getStats();
        connectedToMongo = true;
    }

    @Override
    public void contextDestroyed(javax.servlet.ServletContextEvent sce) {
        // override
    }

    private static void redactJob() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                redactForAccount();
            }
        }, 0, 15, TimeUnit.MINUTES);
    }

    private static void redactForAccount(){
        Context.accountId.set(1718042191);

        List<SampleData> val = new ArrayList<>();
        boolean first = true;
        SampleData lastSd = null;
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.getMetaAll();
        for (ApiCollection apiCollection: apiCollections) {

            do {
                MongoCursor<SampleData> cursor = null;
                if (first) {
                    try {
                    cursor = SampleDataDao.instance.getMCollection().find(Filters.and(Filters.eq("_id.apiCollectionId", apiCollection.getId()), Filters.exists("_id.method"))).sort(Sorts.ascending("_id.url", "_id.method")).limit(1).cursor();
                    first = false;
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    if (lastSd == null) break;

                    Bson filter = 
                    Filters.and(
                        Filters.eq("_id.apiCollectionId", apiCollection.getId()),
                        Filters.or(
                            Filters.gt("_id.url", lastSd.getId().getUrl()),
                            Filters.and(
                                    Filters.eq("_id.url", lastSd.getId().getUrl()),
                                    Filters.gt("_id.method", lastSd.getId().getMethod().name())
                            )

                        )
                    );
                    

                    cursor = SampleDataDao.instance.getMCollection().find(filter).sort(Sorts.ascending("_id.url", "_id.method")).limit(1).cursor();

                }

                if (cursor.hasNext()) {
                    val = Collections.singletonList(cursor.next());
                } else {
                    System.out.println("exiting: ");
                    break;
                }

                if (val.size() == 0) {
                    return;
                }

                SampleData sd = val.get(val.size()-1);
                lastSd = sd;

                System.out.println("sd: " + sd.getId().getApiCollectionId() + " " + sd.getId().getMethod() + " " + sd.getId().getUrl());

                List<String> samples = sd.getSamples();
                boolean changed = false;
                for(int i =0; i < samples.size(); i++) {
                    String sample = samples.get(i);
                    if (!sample.contains("****")) {
                        try {
                        sample = RedactSampleData.redactIfRequired(sample, true, true);
                        samples.set(i, sample);
                        changed = true;
                        } catch (Exception e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }
                    
                }

                if (changed) {
                    System.out.println("Updating.. " + sd.getId().getUrl() + " " + sd.getId().getMethod() + " " + sd.getId().getApiCollectionId());
                    try {
                        SampleData updated = SampleDataDao.instance.updateOne(
                            Filters.and(
                                Filters.eq("_id.apiCollectionId", sd.getId().getApiCollectionId()),
                                Filters.eq("_id.url", sd.getId().getUrl()),
                                Filters.eq("_id.method", sd.getId().getMethod())
                                
                            ),
                            Updates.set("samples", samples)
                        );
                        System.out.println("Updated: " + samples);

                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                        e.printStackTrace();
                    }

                }

            } while(val.size() > 0);
        }

        System.out.println("done");
       
    }

}
