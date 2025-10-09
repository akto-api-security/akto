package com.akto.dto;

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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public String getCrawlId() {
        return crawlId;
    }

    public void setCrawlId(String crawlId) {
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
