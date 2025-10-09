package com.akto.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrawlerUrl {

    public static final String URL = "url";
    private String url;

    public static final String ACCEPTED = "accepted";
    private boolean accepted;

    public static final String TIMESTAMP = "timestamp";
    private int timestamp;

    public static final String CRAWL_ID = "crawlId";
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
