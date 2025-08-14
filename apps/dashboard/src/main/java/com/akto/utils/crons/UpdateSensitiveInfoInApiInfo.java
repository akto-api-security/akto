package com.akto.utils.crons;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.akto.dao.AccountSettingsDao;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AccountTask;
import com.akto.util.LastCronRunInfo;
import com.akto.utils.RiskScoreOfCollections;

public class UpdateSensitiveInfoInApiInfo {

    private static final LoggerMaker loggerMaker = new LoggerMaker(UpdateSensitiveInfoInApiInfo.class, LogDb.DASHBOARD);
    public static final List<Integer> allowedAccounts = Arrays.asList(
        1752208054,
        1751294157,
        1669322524,
        1705869646,
        1723492815,
        1724877069,
        1727251348,
        1729478227,
        1737683011,
        1731351930
    );

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private int cronTime = 15;
    public void setUpSensitiveMapInApiInfoScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        int account = t.getId();
                        if (!allowedAccounts.contains(account)) {
                            loggerMaker.debugAndAddToDb("Skipping sensitive info mapping for account: " + account, LogDb.DASHBOARD);
                            return;
                        }
                        loggerMaker.warnAndAddToDb("Cron for mapping sensitive info picked up by " + account, LogDb.DASHBOARD);
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
