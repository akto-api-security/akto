package com.akto.dto;

import lombok.Getter;
import lombok.Setter;

public class CrawlerUrl {

    public static final String URL = "url";
    @Getter
    @Setter
    private String url;

    public static final String ACCEPTED = "accepted";
    @Getter
    @Setter
    private boolean accepted;

    public static final String TIMESTAMP = "timestamp";
    @Getter
    @Setter
    private int timestamp;

    public static final String CRAWL_ID = "crawlId";
    @Getter
    @Setter
    private String crawlId;

    public CrawlerUrl() {
    }

    public CrawlerUrl(String url, boolean accepted, int timestamp, String crawlId) {
        this.url = url;
        this.accepted = accepted;
        this.timestamp = timestamp;
        this.crawlId = crawlId;
    }

    @Override
    public String toString() {
        return "CrawlerUrl{" +
                "url='" + url + '\'' +
                ", accepted=" + accepted +
                ", timestamp=" + timestamp +
                ", crawlId='" + crawlId + '\'' +
                '}';
    }
}
