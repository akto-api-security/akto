package com.akto.utils.crons;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bson.types.ObjectId;

import com.akto.dao.testing.DeleteTestRunsDao;
import com.akto.dto.testing.DeleteTestRuns;
import com.mongodb.client.model.Filters;

public class Crons {

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void deleteTestRunsScheduler(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run(){
                List<DeleteTestRuns> deleteTestRunsList = DeleteTestRunsDao.instance.findAll(Filters.empty());
                if(deleteTestRunsList != null){
                    for(DeleteTestRuns deleteTestRun : deleteTestRunsList){
                        List<ObjectId> latestSummaryIds = deleteTestRun.getLatestTestingSummaryIds();
                        if(DeleteTestRunsDao.instance.isTestRunDeleted(deleteTestRun)){
                            DeleteTestRunsDao.instance.getMCollection().deleteOne(Filters.in(DeleteTestRuns.LATEST_TESTING_SUMMARY_IDS, latestSummaryIds));
                        }else{
                            DeleteTestRunsDao.instance.deleteTestRunsFromDb(deleteTestRun);
                        }
                    }
                }
            }
        }, 0 , 1, TimeUnit.DAYS);
    }
}
