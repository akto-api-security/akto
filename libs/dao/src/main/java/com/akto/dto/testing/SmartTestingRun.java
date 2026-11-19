package com.akto.dto.testing;

import org.bson.types.ObjectId;

/**
 * One smart-testing run of one test on one API, recorded as it happens so the dashboard can show it live. The
 * smart-testing service starts and finishes it (through cyborg); its steps are SmartTestingEvents and the user's
 * messages to it are SmartTestingMessages, all joined by runKey.
 */
public class SmartTestingRun {

    public enum Status { RUNNING, COMPLETED, STOPPED, SKIPPED, FAILED }

    public static final String ID = "_id";
    private ObjectId id;

    public static final String RUN_KEY = "runKey";
    private String runKey; // set by the smart-testing service, unique per run

    public static final String TESTING_RUN_RESULT_SUMMARY_ID = "testingRunResultSummaryId";
    private String testingRunResultSummaryId; // hex id of the test run's summary
    private int apiCollectionId;
    private String method;
    private String url;
    private String testSubType;

    public static final String STATUS = "status";
    private Status status;
    private String statusReason;

    public static final String STARTED_TS = "startedTs";
    private int startedTs;
    public static final String UPDATED_TS = "updatedTs";
    private int updatedTs;
    public static final String ENDED_TS = "endedTs";
    private int endedTs;

    public SmartTestingRun() { }

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public String getRunKey() { return runKey; }
    public void setRunKey(String runKey) { this.runKey = runKey; }

    public String getTestingRunResultSummaryId() { return testingRunResultSummaryId; }
    public void setTestingRunResultSummaryId(String testingRunResultSummaryId) { this.testingRunResultSummaryId = testingRunResultSummaryId; }

    public int getApiCollectionId() { return apiCollectionId; }
    public void setApiCollectionId(int apiCollectionId) { this.apiCollectionId = apiCollectionId; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTestSubType() { return testSubType; }
    public void setTestSubType(String testSubType) { this.testSubType = testSubType; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }

    public int getStartedTs() { return startedTs; }
    public void setStartedTs(int startedTs) { this.startedTs = startedTs; }

    public int getUpdatedTs() { return updatedTs; }
    public void setUpdatedTs(int updatedTs) { this.updatedTs = updatedTs; }

    public int getEndedTs() { return endedTs; }
    public void setEndedTs(int endedTs) { this.endedTs = endedTs; }
}
