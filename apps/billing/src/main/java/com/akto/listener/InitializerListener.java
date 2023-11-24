package com.akto.listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.support.MetricType;

import com.akto.DaoInit;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AccountsDao;
import com.akto.dao.context.Context;
import com.akto.dao.notifications.SlackWebhooksDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dao.usage.UsageSyncDao;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.billing.Organization;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.UsageSync;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AccountTask;
import com.akto.util.OrganizationTask;
import com.itextpdf.text.pdf.events.IndexEvents.Entry;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class InitializerListener implements ServletContextListener {
    
    public static boolean connectedToMongo = false;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static final LoggerMaker loggerMaker = new LoggerMaker(InitializerListener.class);

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {

        String mongoURI = System.getenv("BILLING_DB_CONN_URL");
        System.out.println("MONGO URI " + mongoURI);
        DaoInit.init(new ConnectionString(mongoURI));

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
                        runInitializerFunctions();
                        setupUsageReportingScheduler();
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

    public static void runInitializerFunctions() {
        System.out.println("Running initializer functions");
    }

    @Override
    public void contextDestroyed(javax.servlet.ServletContextEvent sce) {
        // override
    }

     public void setupUsageReportingScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                loggerMaker.infoAndAddToDb(String.format("Running usage reporting scheduler"), LogDb.BILLING);

                UsageSync usageSync = UsageSyncDao.instance.findOne(
                    Filters.eq(UsageSync.SERVICE, "billing")
                );

                if (usageSync == null) {
                    usageSync = new UsageSync();
                    usageSync.setLastSyncStartEpoch(0);
                }

                int usageLowerBound = usageSync.getLastSyncStartEpoch();
                int usageUpperBound = Context.now();

                UsageSyncDao.instance.updateOne(
                    Filters.eq(UsageSync.SERVICE, "billing"),
                    Updates.set(UsageSync.LAST_SYNC_START_EPOCH, usageUpperBound)
                );

                OrganizationTask.instance.executeTask(new Consumer<Organization>() {
                    @Override
                    public void accept(Organization o) {
                        String organizationId = o.getId();
                        String organizationName = o.getName();
                        Set<Integer> accounts = o.getAccounts();

                        loggerMaker.infoAndAddToDb(String.format("Reporting usage for organization %s - %s", organizationId, organizationName), LogDb.BILLING);
                        
                        loggerMaker.infoAndAddToDb(String.format("Calculating Consolidated and account wise usage for organization %s - %s", organizationId, organizationName), LogDb.BILLING);

                        Map<String, Integer> consolidatedUsage = new HashMap<String, Integer>();
                        Map<Integer, Map<MetricTypes, Integer>> accountWiseUsage = new HashMap<Integer, Map<MetricTypes, Integer>>();

                        // Initialize account wise usage
                        for (Integer account: accounts) {
                            Map<MetricTypes, Integer> accountUsage = new HashMap<MetricTypes, Integer>();
                            
                            for (MetricTypes metricType : MetricTypes.values()) {
                                accountUsage.put(metricType, 0);
                            }

                            accountWiseUsage.put(account, accountUsage);
                        }

                        // Calculate account wise usage and consolidated usage
                        for (MetricTypes metricType : MetricTypes.values()) {   
                            consolidatedUsage.put(metricType.toString(), 0);

                            for (int account: accounts) {
                                UsageMetric usageMetric = UsageMetricsDao.instance.findLatestOne(
                                    Filters.and(
                                        Filters.eq(UsageMetric.ORGANIZATION_ID, organizationId),
                                        Filters.eq(UsageMetric.ACCOUNT_ID, account),
                                        Filters.eq(UsageMetric.METRIC_TYPE, metricType),
                                        Filters.and(
                                                Filters.gte(UsageMetric.AKTO_SAVE_EPOCH, usageLowerBound),
                                                Filters.lt(UsageMetric.AKTO_SAVE_EPOCH, usageUpperBound)
                                            )
                                        )
                                    );

                                int usage = 0;

                                if (usageMetric != null) {
                                    usage = usageMetric.getUsage();
                                }

                                int currentConsolidateUsage = consolidatedUsage.get(metricType.toString());
                                int updatedConsolidateUsage = currentConsolidateUsage + usage;

                                consolidatedUsage.put(metricType.toString(), updatedConsolidateUsage);
                            }           
                        }

                        loggerMaker.infoAndAddToDb(String.format("Consolidated and account wise usage for organization %s - %s calculated", organizationId, organizationName), LogDb.BILLING);
                        
                        // for(Map.Entry<Integer, Map<MetricTypes, Integer>> entry : accountWiseUsage.entrySet()) {
                        //     int accountId = entry.getKey();
                        //     Map<MetricTypes, Integer> au = entry.getValue();

                        //     System.out.println(accountId);

                        //     for(Map.Entry<MetricTypes, Integer> entry2 : au.entrySet()) {
                        //         MetricTypes metricType = entry2.getKey();
                        //         int usage = entry2.getValue();
                        //         System.out.println(String.format("%s - %d", metricType, usage));
                        //     }

                        // }

                        // for(Map.Entry<String, Integer> entry : consolidatedUsage.entrySet()) {
                        //     String metricType = entry.getKey();
                        //     int usage = entry.getValue();
                        //     System.out.println(String.format("%s - %s", metricType, usage));
                        // }
                        
                    }
                }, "usage-reporting-scheduler");
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

}
