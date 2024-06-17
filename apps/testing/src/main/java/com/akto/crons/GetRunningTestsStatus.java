package com.akto.crons;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bson.types.ObjectId;

import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.Account;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.util.AccountTask;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class GetRunningTestsStatus {
    private final ConcurrentHashMap<ObjectId, TestingRun.State> currentRunningTestsMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private GetRunningTestsStatus () {
    }

    private static final GetRunningTestsStatus getRunningTestsStatus = new GetRunningTestsStatus();

    public static GetRunningTestsStatus getRunningTests() {
        return getRunningTestsStatus;
    }
 
    public void getStatusOfRunningTests(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run(){
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            int timeFilter = Context.now() - 30 * 60;
                            List<TestingRunResultSummary> currentRunningTests = TestingRunResultSummariesDao.instance.findAll(
                                Filters.gte(TestingRunResultSummary.START_TIMESTAMP, timeFilter),
                                Projections.include("_id", TestingRunResultSummary.STATE, TestingRunResultSummary.TESTING_RUN_ID) 
                            );
                            for(TestingRunResultSummary trrs : currentRunningTests){
                                if(trrs.getState() == TestingRun.State.COMPLETED){
                                    currentRunningTestsMap.remove(trrs.getId());
                                    currentRunningTestsMap.remove(trrs.getTestingRunId());
                                }else{
                                    currentRunningTestsMap.put(trrs.getId(), trrs.getState());
                                    currentRunningTestsMap.put(trrs.getTestingRunId(), trrs.getState());
                                }
                            } 
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                },"get-current-running-tests");
            }
        }, 0 , 1, TimeUnit.MINUTES);
    }

    public ConcurrentHashMap<ObjectId, TestingRun.State> getCurrentRunningTestsMap() {
        return currentRunningTestsMap;
    }

    public boolean isTestRunning(ObjectId runId){
        if(currentRunningTestsMap == null || !currentRunningTestsMap.containsKey(runId) || currentRunningTestsMap.get(runId).equals(TestingRun.State.RUNNING)){
            return true;
        }else{
            return false;
        }
    }
    
    public TestingRun.State getCurrentState(ObjectId runId){
        if(currentRunningTestsMap == null || !currentRunningTestsMap.containsKey(runId)){
            return null;
        }
        return currentRunningTestsMap.get(runId);
    }
}