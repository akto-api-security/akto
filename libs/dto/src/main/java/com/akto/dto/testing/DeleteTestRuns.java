package com.akto.dto.testing;

import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;

public class DeleteTestRuns {
    
    public static final String TEST_RUNS_IDS = "testRunIds";
    private List<ObjectId> testRunIds;

    public static final String LAST_PICKED_UP = "lastPickedUp";
    private int lastPickedUp;

    public static final String TESTING_COLLECTIONS_DELETED = "testingCollectionsDeleted";
    private Map<String, Boolean> testingCollectionsDeleted;

    public static final String TEST_CONFIG_IDS = "testConfigIds";
    private List<Integer> testConfigIds;

    public static final String LATEST_TESTING_SUMMARY_IDS = "latestTestingSummaryIds";
    private List<ObjectId> latestTestingSummaryIds;

    public DeleteTestRuns(){}

    public DeleteTestRuns(List<ObjectId> testRunIds, int lastPickedUp, Map<String, Boolean> testingCollectionsDeleted, List<Integer> testConfigIds, List<ObjectId> latestTestingSummaryIds){
        this.testRunIds = testRunIds;
        this.lastPickedUp = lastPickedUp;
        this.testingCollectionsDeleted = testingCollectionsDeleted;
        this.testConfigIds = testConfigIds;
        this.latestTestingSummaryIds = latestTestingSummaryIds;
    }

    public Map<String, Boolean> getTestingCollectionsDeleted() {
        return testingCollectionsDeleted;
    }
    public void setTestingCollectionsDeleted(Map<String, Boolean> testingCollectionsDeleted) {
        this.testingCollectionsDeleted = testingCollectionsDeleted;
    }
    public int getLastPickedUp() {
        return lastPickedUp;
    }
    public void setLastPickedUp(int lastPickedUp) {
        this.lastPickedUp = lastPickedUp;
    }
    public List<ObjectId> getTestRunIds() {
        return testRunIds;
    }
    public void setTestRunIds(List<ObjectId> testRunIds) {
        this.testRunIds = testRunIds;
    }

    public List<Integer> getTestConfigIds() {
        return testConfigIds;
    }

    public void setTestConfigIds(List<Integer> testConfigIds) {
        this.testConfigIds = testConfigIds;
    }

    public List<ObjectId> getLatestTestingSummaryIds() {
        return latestTestingSummaryIds;
    }

    public void setLatestTestingSummaryIds(List<ObjectId> latestTestingSummaryIds) {
        this.latestTestingSummaryIds = latestTestingSummaryIds;
    }
}
