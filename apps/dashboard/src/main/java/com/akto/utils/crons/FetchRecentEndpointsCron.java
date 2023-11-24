package com.akto.utils.crons;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.akto.action.observe.InventoryAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ActivitiesDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.AccountSettings.LastCronRunInfo;
import com.akto.task.Cluster;
import com.akto.util.AccountTask;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import static com.akto.task.Cluster.callDibs;

public class FetchRecentEndpointsCron {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpRecentEndpointsActivityScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                boolean dibs = callDibs(Cluster.FETCHED_RECENT_ENDPOINTS_COUNT, 300, 60);
                if(!dibs){
                    return;
                }
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                        LastCronRunInfo lastRunTimerInfo = accountSettings.getLastUpdatedCronInfo();

                        try {
                            int endTs = Context.now();
                            int startTs = 0 ;
                            if(lastRunTimerInfo == null){
                                startTs = Context.now() - (60 * 10);
                            }else{
                                startTs = lastRunTimerInfo.getLastCheckedNewEndpoints();
                            }

                            long newEndpoints  = new InventoryAction().fetchRecentEndpoints(startTs,endTs).size();
                            if(newEndpoints > 0){
                                ActivitiesDao.instance.insertActivity("Endpoints detected",newEndpoints + " new endpoints detected in dashboard");
                            }

                            AccountSettingsDao.instance.getMCollection().updateOne(
                                AccountSettingsDao.generateFilter(),
                                Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_CHECKED_NEW_ENDPOINTS), endTs),
                                new UpdateOptions().upsert(true)
                            );
                        } catch (Exception e) {
                           e.printStackTrace();
                        }
                    }
                }, "fetched-recent-endpoints-count");
            }
        }, 0, 5, TimeUnit.MINUTES);
    }
}
