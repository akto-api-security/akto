package com.akto.dto;

public class CrawlerRun {

    public static final String STARTED_BY = "startedBy";
    private String startedBy;

    public static final String START_TIMESTAMP = "startTimestamp";
    private int startTimestamp;

    public static final String END_TIMESTAMP = "endTimestamp";
    private int endTimestamp;

    public static final String CRAWL_ID = "crawlId";
    private String crawlId;

    public static final String HOSTNAME = "hostname";
    private String hostname;

    public static final String OUT_SCOPE_URLS = "outScopeUrls";
    private String outScopeUrls;

    public CrawlerRun() {
    }

    public CrawlerRun(String startedBy, int startTimestamp, int endTimestamp, String crawlId, String hostname, String outScopeUrls) {
        this.startedBy = startedBy;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.crawlId = crawlId;
        this.hostname = hostname;
        this.outScopeUrls = outScopeUrls;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public void setStartedBy(String startedBy) {
        this.startedBy = startedBy;
    }

    public int getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public int getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public String getCrawlId() {
        return crawlId;
    }

    public void setCrawlId(String crawlId) {
        this.crawlId = crawlId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getOutScopeUrls() {
        return outScopeUrls;
    }

    public void setOutScopeUrls(String outScopeUrls) {
        this.outScopeUrls = outScopeUrls;
    }

    @Override
    public String toString() {
        return "CrawlerRun{" +
                "startedBy='" + startedBy + '\'' +
                ", startTimestamp=" + startTimestamp +
                ", endTimestamp=" + endTimestamp +
                ", crawlId='" + crawlId + '\'' +
                ", hostname='" + hostname + '\'' +
                ", outScopeUrls=" + outScopeUrls +
                '}';
    }
}
