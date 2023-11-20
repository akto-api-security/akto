package com.akto.dto.test_run_findings;

import com.akto.util.enums.GlobalEnums;

import java.util.Arrays;
import java.util.List;

import org.bson.types.ObjectId;

public class TestingRunIssues {
    public static final String TEST_RUN_ISSUES_STATUS = "testRunIssueStatus";

    private GlobalEnums.TestRunIssueStatus testRunIssueStatus;
    private TestingIssuesId id;
    public static final String LAST_SEEN = "lastSeen";
    private int lastSeen;
    public static final String CREATION_TIME = "creationTime";
    private int creationTime;
    public static final String KEY_SEVERITY = "severity";
    private GlobalEnums.Severity severity;
    public static final String LATEST_TESTING_RUN_SUMMARY_ID = "latestTestingRunSummaryId";
    private ObjectId latestTestingRunSummaryId;
    public static final String IGNORE_REASON = "ignoreReason";
    private String ignoreReason;

    private List<Integer> collectionIds;

    public TestingRunIssues(TestingIssuesId id, GlobalEnums.Severity severity, GlobalEnums.TestRunIssueStatus status,
                            int creationTime, int lastSeen, ObjectId latestTestingRunSummaryId) {
        this.creationTime = creationTime;
        this.lastSeen = lastSeen;
        this.id = id;
        if(id !=null && id.getApiInfoKey()!=null){
            this.collectionIds = Arrays.asList(id.getApiInfoKey().getApiCollectionId());
        }
        this.severity = severity;
        this.testRunIssueStatus = status;
        this.latestTestingRunSummaryId = latestTestingRunSummaryId;
    }

    public TestingRunIssues() {
    }

    public void setTestRunIssueStatus(GlobalEnums.TestRunIssueStatus testRunIssueStatus) {
        this.testRunIssueStatus = testRunIssueStatus;
    }

    public void setLastSeen(int lastSeen) {
        this.lastSeen = lastSeen;
    }

    public void setCreationTime(int creationTime) {
        this.creationTime = creationTime;
    }

    public void setSeverity(GlobalEnums.Severity severity) {
        this.severity = severity;
    }

    public void setId(TestingIssuesId id) {
        this.id = id;
        if(id !=null && id.getApiInfoKey()!=null){
            this.collectionIds = Arrays.asList(id.getApiInfoKey().getApiCollectionId());
        }
    }

    public TestingIssuesId getId() {
        return id;
    }

    public int getCreationTime() {
        return creationTime;
    }

    public GlobalEnums.TestRunIssueStatus getTestRunIssueStatus() {
        return testRunIssueStatus;
    }

    public int getLastSeen() {
        return lastSeen;
    }

    public GlobalEnums.Severity getSeverity() {
        return severity;
    }

    public ObjectId getLatestTestingRunSummaryId() {
        return latestTestingRunSummaryId;
    }

    public void setLatestTestingRunSummaryId(ObjectId latestTestingRunSummaryId) {
        this.latestTestingRunSummaryId = latestTestingRunSummaryId;
    }

    public String getIgnoreReason() {
        return ignoreReason;
    }

    public void setIgnoreReason(String ignoreReason) {
        this.ignoreReason = ignoreReason;
    }

    public List<Integer> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<Integer> collectionIds) {
        this.collectionIds = collectionIds;
    }
}
