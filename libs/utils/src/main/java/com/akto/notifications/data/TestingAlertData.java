package com.akto.notifications.data;

import java.util.List;

import com.akto.notifications.slack.NewIssuesModel;

public class TestingAlertData {
    private String title;
    private int critical;
    private int high;
    private int medium;
    private int low;
    private int vulnerableApis;
    private int newIssues;
    private int totalApis;
    private String collection;
    private long scanTimeInSeconds;
    private String testType;
    private long nextTestRun;
    private List<NewIssuesModel> newIssuesList;
    private String viewOnAktoURL;
    private String exportReportUrl;

    public TestingAlertData(){
    }

    public TestingAlertData(String title, int critical, int high, int medium, int low, int vulnerableApis,
                            int newIssues, int totalApis, String collection, long scanTimeInSeconds,
                            String testType, long nextTestRun, List<NewIssuesModel> newIssuesList,
                            String viewOnAktoURL, String exportReportUrl) {
        this.title = title;
        this.critical = critical;
        this.high = high;
        this.medium = medium;
        this.low = low;
        this.vulnerableApis = vulnerableApis;
        this.newIssues = newIssues;
        this.totalApis = totalApis;
        this.collection = collection;
        this.scanTimeInSeconds = scanTimeInSeconds;
        this.testType = testType;
        this.nextTestRun = nextTestRun;
        this.newIssuesList = newIssuesList;
        this.viewOnAktoURL = viewOnAktoURL;
        this.exportReportUrl = exportReportUrl;
    }

    // Getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getCritical() { return critical; }
    public void setCritical(int critical) { this.critical = critical; }

    public int getHigh() { return high; }
    public void setHigh(int high) { this.high = high; }

    public int getMedium() { return medium; }
    public void setMedium(int medium) { this.medium = medium; }

    public int getLow() { return low; }
    public void setLow(int low) { this.low = low; }

    public int getVulnerableApis() { return vulnerableApis; }
    public void setVulnerableApis(int vulnerableApis) { this.vulnerableApis = vulnerableApis; }

    public int getNewIssues() { return newIssues; }
    public void setNewIssues(int newIssues) { this.newIssues = newIssues; }

    public int getTotalApis() { return totalApis; }
    public void setTotalApis(int totalApis) { this.totalApis = totalApis; }

    public String getCollection() { return collection; }
    public void setCollection(String collection) { this.collection = collection; }

    public long getScanTimeInSeconds() { return scanTimeInSeconds; }
    public void setScanTimeInSeconds(long scanTimeInSeconds) { this.scanTimeInSeconds = scanTimeInSeconds; }

    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }

    public long getNextTestRun() { return nextTestRun; }
    public void setNextTestRun(long nextTestRun) { this.nextTestRun = nextTestRun; }

    public List<NewIssuesModel> getNewIssuesList() { return newIssuesList; }
    public void setNewIssuesList(List<NewIssuesModel> newIssuesList) { this.newIssuesList = newIssuesList; }

    public String getViewOnAktoURL() { return viewOnAktoURL; }
    public void setViewOnAktoURL(String viewOnAktoURL) { this.viewOnAktoURL = viewOnAktoURL; }

    public String getExportReportUrl() { return exportReportUrl; }
    public void setExportReportUrl(String exportReportUrl) { this.exportReportUrl = exportReportUrl; }
}
