package com.akto.dto;

import java.util.List;

public class TestReport {
    List<String> reportEncoded;
    public static final String _ACCOUNT_ID = "accountId";
    int accountId;
    public static final String _TESTING_RUN_RESULT_SUMMARY_ID = "testingRunResultSummaryId";
    String testingRunResultSummaryId;
    public static final String _TESTING_RUN_ID = "testingRunId";
    String testingRunId;

    public TestReport(List<String> reportEncoded, int accountId, String testingRunResultSummaryId,
            String testingRunId) {
        this.reportEncoded = reportEncoded;
        this.accountId = accountId;
        this.testingRunResultSummaryId = testingRunResultSummaryId;
        this.testingRunId = testingRunId;
    }

    public TestReport() {
    }

    public List<String> getReportEncoded() {
        return reportEncoded;
    }

    public void setReportEncoded(List<String> reportEncoded) {
        this.reportEncoded = reportEncoded;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public String getTestingRunResultSummaryId() {
        return testingRunResultSummaryId;
    }

    public void setTestingRunResultSummaryId(String testingRunResultSummaryId) {
        this.testingRunResultSummaryId = testingRunResultSummaryId;
    }

    public String getTestingRunId() {
        return testingRunId;
    }

    public void setTestingRunId(String testingRunId) {
        this.testingRunId = testingRunId;
    }


}