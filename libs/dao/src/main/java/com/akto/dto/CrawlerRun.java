package com.akto.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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
