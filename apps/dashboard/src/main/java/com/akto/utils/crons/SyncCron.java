package com.akto.utils.crons;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bson.conversions.Bson;

import com.akto.action.observe.InventoryAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ActivitiesDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.task.Cluster;
import com.akto.util.AccountTask;
import com.akto.util.LastCronRunInfo;
import com.akto.utils.RiskScoreOfCollections;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import static com.akto.task.Cluster.callDibs;

public class SyncCron {
    private static final LoggerMaker loggerMaker = new LoggerMaker(SyncCron.class);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpUpdateCronScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        boolean dibs = callDibs(Cluster.SYNC_CRON_INFO, 300, 60);
                        if(!dibs){
                            loggerMaker.infoAndAddToDb("Cron for updating new parameters, new endpoints and severity score dibs not acquired, thus skipping cron", LogDb.DASHBOARD);
                            return;
                        }
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                        LastCronRunInfo lastRunTimerInfo = accountSettings.getLastUpdatedCronInfo();
                        loggerMaker.infoAndAddToDb("Cron for updating new parameters, new endpoints and severity score picked up " + accountSettings.getId(), LogDb.DASHBOARD);
                        try {
                            int endTs = Context.now();
                            int startTs = 0 ;
                            if(lastRunTimerInfo == null){
                                startTs = Context.now() - (60 * 10);
                            }else{
                                startTs = lastRunTimerInfo.getLastSyncedCron();
                            }
                            
                            // synced new parameters from STI and then inserted into activities
                            Bson filter = Filters.and(Filters.gte(SingleTypeInfo._TIMESTAMP, startTs),Filters.lte(SingleTypeInfo._TIMESTAMP, endTs));
                            long newParams = SingleTypeInfoDao.instance.getMCollection().countDocuments(filter);
                            
                            if(newParams > 0){
                                ActivitiesDao.instance.insertActivity("Parameters detected",newParams + " new parameters detected.");
                            }

                             // synced new endpoints from APIinfo and then inserted into activities
                            long newEndpoints  = new InventoryAction().fetchRecentEndpoints(startTs,endTs).size();
                            if(newEndpoints > 0){
                                ActivitiesDao.instance.insertActivity("Endpoints detected",newEndpoints + " new endpoints detected in dashboard");
                            }

                            // updated {Severity score field in APIinfo}
                            RiskScoreOfCollections updateRiskScore = new RiskScoreOfCollections();
                            updateRiskScore.updateSeverityScoreInApiInfo(startTs);

                            AccountSettingsDao.instance.getMCollection().updateOne(
                                AccountSettingsDao.generateFilter(),
                                Updates.combine(
                                    Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_SYNCED_CRON), endTs),
                                    Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_UPDATED_SEVERITY), endTs)
                                ),
                                new UpdateOptions().upsert(true)
                            );
                        } catch (Exception e) {
                           e.printStackTrace();
                        }
                    }
                }, "sync-cron-info");
            }
        }, 0, 5, TimeUnit.MINUTES);
    }
}
