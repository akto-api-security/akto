package com.akto.merging;

import com.akto.dao.context.Context;
import com.akto.data_actor.DbLayer;
import com.akto.dto.Account;
import com.akto.log.LoggerMaker;
import com.akto.util.AccountTask;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Cron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Cron.class);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void cron(boolean isHybridSaas) {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                if (isHybridSaas) {
                    AccountTask.instance.executeTaskHybridAccounts(new Consumer<Account>() {
                        @Override
                        public void accept(Account t) {
                            triggerMerging(t.getId());
                        }
                    }, "mergingCron");
                } else {
                    AccountTask.instance.executeTask(new Consumer<Account>() {
                        @Override
                        public void accept(Account t) {
                            triggerMerging(t.getId());
                        }
                    }, "mergingCron");
                }                
            }
        }, 0, 10, TimeUnit.MINUTES);

    }

    public void triggerMerging(int accountId) {
        if (!Lock.acquireLock(accountId)) {
            loggerMaker.infoAndAddToDb("Unable to acquire lock, merging process ignored for account " + accountId, LoggerMaker.LogDb.RUNTIME);
            return;
        }
        loggerMaker.infoAndAddToDb("Acquired lock, starting merging process for account " + accountId, LoggerMaker.LogDb.RUNTIME);
        List<Integer> apiCollectionIds = DbLayer.fetchApiCollectionIds();
        try {
            for (int apiCollectionId : apiCollectionIds) {
                int start = Context.now();
                loggerMaker.infoAndAddToDb("Started merging API collection " + apiCollectionId +
                        " accountId " + accountId, LoggerMaker.LogDb.RUNTIME);
                try {
                    MergingLogic.mergeUrlsAndSave(apiCollectionId, true);
                    loggerMaker.infoAndAddToDb("Finished merging API collection " +
                            apiCollectionId + " accountId " + accountId + " in " + (Context.now() - start)
                            + " seconds", LoggerMaker.LogDb.RUNTIME);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error merging Api collection" + apiCollectionId +
                            " accountId " + accountId + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
                }
            }
        } catch (Exception e) {
            String err = e.getStackTrace().length > 0 ? e.getStackTrace()[0].toString() : e.getMessage();
            loggerMaker.errorAndAddToDb("error in mergeUrlsAndSave: " + " accountId " + accountId
                    + err, LoggerMaker.LogDb.RUNTIME);
            e.printStackTrace();
        }
        Lock.releaseLock(accountId);
    }

}
