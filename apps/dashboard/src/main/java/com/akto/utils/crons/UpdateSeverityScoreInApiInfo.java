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

public class UpdateSeverityScoreInApiInfo {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public void updateSeverityScoreScheduler() {
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
                                updateRiskScore.updateSeverityScoreInApiInfo(0);
                            }else{
                                updateRiskScore.updateSeverityScoreInApiInfo(lastRunTimerInfo.getLastUpdatedIssues());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, "update-severity-score");
            }
        }, 0, 5, TimeUnit.MINUTES);
    }
}
