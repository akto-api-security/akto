package com.akto.dto;

import lombok.Getter;
import lombok.Setter;

public class CrawlerRun {

    public static final String STARTED_BY = "startedBy";
    @Getter
    @Setter
    private String startedBy;

    public static final String START_TIMESTAMP = "startTimestamp";
    @Getter
    @Setter
    private int startTimestamp;

    public static final String END_TIMESTAMP = "endTimestamp";
    @Getter
    @Setter
    private int endTimestamp;

    public static final String CRAWL_ID = "crawlId";
    @Getter
    @Setter
    private String crawlId;

    public static final String HOSTNAME = "hostname";
    @Getter
    @Setter
    private String hostname;

    public static final String OUT_SCOPE_URLS = "outScopeUrls";
    @Getter
    @Setter
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
