package com.akto.dto.testing.info;

import java.util.List;


public class CurrentTestsStatus {
    
    public static final String TEST_RUNS_QUEUED = "testRunsQueued";
    private int testRunsQueued;

    public static final String TOTAL_TESTS_COMPLETED = "totalTestsCompleted";
    private int totalTestsCompleted;

    public static final String TEST_RUNS_SCHEDULED = "testRunsScheduled";
    private int testRunsScheduled;

    public static class StatusForIndividualTest {

        private String testingRunId;
        private int testsInitiated;
        private int testsInsertedInDb;

        public StatusForIndividualTest() {}

        public StatusForIndividualTest(String testingRunId, int testsInitiated, int testsInsertedInDb) {
            this.testingRunId = testingRunId;
            this.testsInitiated = testsInitiated;
            this.testsInsertedInDb = testsInsertedInDb;
        }

        public String getTestingRunId() {
            return testingRunId;
        }

        public void setTestingRunId(String testingRunId) {
            this.testingRunId = testingRunId;
        }

        public int getTestsInitiated() {
            return testsInitiated;
        }

        public void setTestsInitiated(int testsInitiated) {
            this.testsInitiated = testsInitiated;
        }

        public int getTestsInsertedInDb() {
            return testsInsertedInDb;
        }

        public void setTestsInsertedInDb(int testsInsertedInDb) {
            this.testsInsertedInDb = testsInsertedInDb;
        }
        
    }

    private List<StatusForIndividualTest> currentRunningTestsStatus;

    public CurrentTestsStatus () {}

    public CurrentTestsStatus(int testRunsQueued, int totalTestsCompleted, int testRunsScheduled, List<StatusForIndividualTest> currentRunningTestsStatus){
        this.testRunsQueued = testRunsQueued;
        this.testRunsScheduled = testRunsScheduled;
        this.totalTestsCompleted = totalTestsCompleted;
        this.currentRunningTestsStatus = currentRunningTestsStatus;
    }

    public int getTestRunsQueued() {
        return testRunsQueued;
    }
    public void setTestRunsQueued(int testRunsQueued) {
        this.testRunsQueued = testRunsQueued;
    }

    public int getTotalTestsCompleted() {
        return totalTestsCompleted;
    }
    public void setTotalTestsCompleted(int totalTestsCompleted) {
        this.totalTestsCompleted = totalTestsCompleted;
    }
    
    public int getTestRunsScheduled() {
        return testRunsScheduled;
    }
    public void setTestRunsScheduled(int testRunsScheduled) {
        this.testRunsScheduled = testRunsScheduled;
    }

    public List<StatusForIndividualTest> getCurrentRunningTestsStatus() {
        return currentRunningTestsStatus;
    }

    public void setCurrentRunningTestsStatus(List<StatusForIndividualTest> currentRunningTestsStatus) {
        this.currentRunningTestsStatus = currentRunningTestsStatus;
    }

}
