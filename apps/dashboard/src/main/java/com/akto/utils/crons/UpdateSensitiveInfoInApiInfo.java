package com.akto.utils.crons;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.task.Cluster;
import com.akto.util.AccountTask;
import com.akto.util.LastCronRunInfo;
import com.akto.utils.RiskScoreOfCollections;

import static com.akto.task.Cluster.callDibs;

public class UpdateSensitiveInfoInApiInfo {

    private static final LoggerMaker loggerMaker = new LoggerMaker(UpdateSensitiveInfoInApiInfo.class, LogDb.DASHBOARD);

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private int cronTime = 15;
    public void setUpSensitiveMapInApiInfoScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {

                Context.accountId.set(1000_000);
                boolean dibs = callDibs(Cluster.MAP_SENSITIVE_IN_INFO, 900, 60);
                if(!dibs){
                    loggerMaker.debugAndAddToDb("Cron for mapping sensitive info not picked up as dibs is acquired.", LogDb.DASHBOARD);
                    return;
                }
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                        loggerMaker.debugAndAddToDb("Cron for mapping sensitive info picked up by " + accountSettings.getId(), LogDb.DASHBOARD);
                        LastCronRunInfo lastRunTimerInfo = accountSettings.getLastUpdatedCronInfo();
                        RiskScoreOfCollections updateRiskScore = new RiskScoreOfCollections();
                        try {
                            if(lastRunTimerInfo == null){
                                updateRiskScore.mapSensitiveSTIsInApiInfo(0, cronTime);
                            }else{
                                updateRiskScore.mapSensitiveSTIsInApiInfo(lastRunTimerInfo.getLastUpdatedSensitiveMap(),cronTime);
                            }
                        } catch (Exception e) {
                           e.printStackTrace();
                        }
                    }
                }, "map-sensitiveInfo-in-ApiInfo");
            }
        }, 0, cronTime, TimeUnit.MINUTES);
    }
}
