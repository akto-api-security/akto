package com.akto.dto.testing.sources;

import java.util.List;
import java.util.Map;

public class ThreatReports {

    public static final String FILTERS_FOR_REPORT = "filtersForReport";
    private Map<String, List<String>> filtersForReport;

    public static final String TIMESTAMP = "timestamp";
    private int timestamp;

    public static final String PDF_REPORT_STRING = "pdfReportString";
    private String pdfReportString;

    public static final String THREAT_IDS_FOR_REPORT = "threatIdsForReport";
    private List<String> threatIdsForReport;

    public ThreatReports() {}

    public ThreatReports(Map<String, List<String>> filtersForReport, int timestamp, String pdfReportString, List<String> threatIdsForReport) {
        this.filtersForReport = filtersForReport;
        this.timestamp = timestamp;
        this.pdfReportString = pdfReportString;
        this.threatIdsForReport = threatIdsForReport;
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

    public String getPdfReportString() {
        return pdfReportString;
    }

    public void setPdfReportString(String pdfReportString) {
        this.pdfReportString = pdfReportString;
    }

    public List<String> getThreatIdsForReport() {
        return threatIdsForReport;
    }

    public void setThreatIdsForReport(List<String> threatIdsForReport) {
        this.threatIdsForReport = threatIdsForReport;
    }
}
