package com.akto.crons;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.bson.types.ObjectId;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

public class GetRunningTestsStatus {
    private final ConcurrentHashMap<ObjectId, TestingRun.State> currentRunningTestsMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static final DataActor dataActor = DataActorFactory.fetchInstance();
    private static final LoggerMaker loggerMaker = new LoggerMaker(GetRunningTestsStatus.class, LogDb.TESTING);
    private GetRunningTestsStatus () {
    }

    private static final GetRunningTestsStatus getRunningTestsStatus = new GetRunningTestsStatus();

    public static GetRunningTestsStatus getRunningTests() {
        return getRunningTestsStatus;
    }
 
    public void getStatusOfRunningTests(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run(){
            try {

                List<TestingRunResultSummary> currentRunningTests = dataActor.fetchStatusOfTests();
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
                loggerMaker.errorAndAddToDb(e, "Error in getStatusOfRunningTests");
            }
        }
        }, 0 , 10, TimeUnit.SECONDS);
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