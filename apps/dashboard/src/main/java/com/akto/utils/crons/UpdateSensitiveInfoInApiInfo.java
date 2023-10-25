package com.akto.utils.crons;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.akto.dao.AccountSettingsDao;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.AccountSettings.LastCronRunInfo;
import com.akto.util.AccountTask;
import com.akto.utils.RiskScoreOfCollections;

public class UpdateSensitiveInfoInApiInfo {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private int cronTime = 15;
    public void setUpSensitiveMapInApiInfoScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
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
