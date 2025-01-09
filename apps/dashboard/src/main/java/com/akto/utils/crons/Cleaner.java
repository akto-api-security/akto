package com.akto.utils.crons;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dao.settings.CommonUtilsDao;
import com.akto.dto.settings.CommonUtils;
import com.akto.log.LoggerMaker;
import com.mongodb.client.model.Filters;

public class Cleaner {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static LoggerMaker logger = new LoggerMaker(Cleaner.class, LoggerMaker.LogDb.DASHBOARD);
    private int BATCH_SIZE = 5;

    public void deleteTestResultsScheduler(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run(){
                try {
                    logger.infoAndAddToDb("Cleaner cron picker up");
                    CommonUtils currentDoc = CommonUtilsDao.instance.findOne(
                        Filters.eq(CommonUtils.JOB_TYPE, CommonUtils.JOB_TYPES.DELETE_NON_VULNERABLE_TESTS)
                    );
                    if(currentDoc == null || currentDoc.getAccountIds().isEmpty()){
                        return;
                    }

                    for(int accountId: currentDoc.getAccountIds()){

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0 , 30, TimeUnit.MINUTES);
    }

}
