package com.akto.dto.testing.sources;

import java.util.List;
import java.util.Map;

import com.akto.dto.test_run_findings.TestingIssuesId;

public class TestReports {
    
    public static final String FILTERS_FOR_REPORT = "filtersForReport";
    private Map<String, List<String>> filtersForReport;

    public static final String TIMESTAMP = "timestamp";
    private int timestamp;

    public static final String PDF_REPORT_STRING = "pdfReportString";
    private String pdfReportString;

    public static final String ISSUE_IDS_FOR_REPORT = "issuesIdsForReport";
    private List<TestingIssuesId> issuesIdsForReport;

    public TestReports () {}

    public TestReports (Map<String, List<String>> filtersForReport, int timestamp, String pdfReportString, List<TestingIssuesId> issuesIdsForReport){
        this.filtersForReport = filtersForReport;
        this.timestamp = timestamp;
        this.pdfReportString = pdfReportString;
        this.issuesIdsForReport = issuesIdsForReport;
    }


    public String getPdfReportString() {
        return pdfReportString;
    }

    public void setPdfReportString(String pdfReportString) {
        this.pdfReportString = pdfReportString;
    }

    public Map<String, List<String>> getFiltersForReport() {
        return filtersForReport;
    }

    public void setFiltersForReport(Map<String, List<String>> filtersForReport) {
        this.filtersForReport = filtersForReport;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public List<TestingIssuesId> getIssuesIdsForReport() {
        return issuesIdsForReport;
    }

    public void setIssuesIdsForReport(List<TestingIssuesId> issuesIdsForReport) {
        this.issuesIdsForReport = issuesIdsForReport;
    }
}
