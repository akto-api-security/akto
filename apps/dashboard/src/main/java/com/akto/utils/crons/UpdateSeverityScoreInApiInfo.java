package com.akto.utils.crons;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.akto.dao.AccountSettingsDao;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.AccountSettings.LastCronRunInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.task.Cluster;
import com.akto.util.AccountTask;
import com.akto.utils.RiskScoreOfCollections;

import static com.akto.task.Cluster.callDibs;

public class UpdateSeverityScoreInApiInfo {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final LoggerMaker loggerMaker = new LoggerMaker(UpdateSeverityScoreInApiInfo.class);
    public void updateSeverityScoreScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                boolean dibs = callDibs(Cluster.UPDATE_SEVERITY_SCORE, 300, 60);
                if(!dibs){
                    loggerMaker.infoAndAddToDb("Cron for calculating severity info for apiInfo dibs not acquired, thus skipping cron", LogDb.DASHBOARD);
                    return;
                }
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {       
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                        LastCronRunInfo lastRunTimerInfo = accountSettings.getLastUpdatedCronInfo();
                        loggerMaker.infoAndAddToDb("Cron for calculating severity info for apiInfo picked up by " + accountSettings.getId(), LogDb.DASHBOARD);
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
