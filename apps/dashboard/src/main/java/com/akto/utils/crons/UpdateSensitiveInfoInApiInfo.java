package com.akto.utils.crons;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private int cronTime = 15;
    public void setUpSensitiveMapInApiInfoScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                long runStartMs = System.currentTimeMillis();
                Set<Integer> accountsWithRecentUserLogin = DormantAccountFilter.fetchAccountsWithRecentUserLogin();
                AtomicInteger processedAccounts = new AtomicInteger();
                AtomicInteger dormantAccounts = new AtomicInteger();
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        int account = t.getId();
                        if (DormantAccountFilter.isDormant(account, accountsWithRecentUserLogin)) {
                            dormantAccounts.incrementAndGet();
                            loggerMaker.debugAndAddToDb("Skipping sensitive info mapping for dormant account: " + account, LogDb.DASHBOARD);
                            return;
                        }
                        processedAccounts.incrementAndGet();
                        loggerMaker.warnAndAddToDb("Cron for mapping sensitive info picked up by " + account, LogDb.DASHBOARD);
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                        loggerMaker.debugAndAddToDb("Cron for mapping sensitive info picked up by " + accountSettings.getId(), LogDb.DASHBOARD);
                        LastCronRunInfo lastRunTimerInfo = accountSettings.getLastUpdatedCronInfo();
                        RiskScoreOfCollections updateRiskScore = new RiskScoreOfCollections();
                        try {
                            updateRiskScore.mapSensitiveSTIsInApiInfo(
                                lastRunTimerInfo == null ? 0 : lastRunTimerInfo.getLastUpdatedSensitiveMap());
                        } catch (Exception e) {
                           e.printStackTrace();
                        }
                    }
                }, "map-sensitiveInfo-in-ApiInfo");
                loggerMaker.info("map-sensitiveInfo-in-ApiInfo run finished: accounts processed: " + processedAccounts.get()
                    + ", skipped as dormant: " + dormantAccounts.get() + ", total: " + (System.currentTimeMillis() - runStartMs) + " ms");
            }
        }, 0, cronTime, TimeUnit.MINUTES);
    }
}
