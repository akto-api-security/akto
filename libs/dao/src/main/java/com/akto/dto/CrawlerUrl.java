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

    public static final String SOURCE_URL = "sourceUrl";
    private String sourceUrl;

    public static final String SOURCE_XPATH = "sourceXpath";
    private String sourceXpath;

    public static final String BUTTON_TEXT = "buttonText";
    private String buttonText;

    public CrawlerUrl() {
    }

    public CrawlerUrl(String url, boolean accepted, int timestamp, String crawlId, String sourceUrl, String sourceXpath, String buttonText) {
        this.url = url;
        this.accepted = accepted;
        this.timestamp = timestamp;
        this.crawlId = crawlId;
        this.sourceUrl = sourceUrl;
        this.sourceXpath = sourceXpath;
        this.buttonText = buttonText;
    }

    @Override
    public String toString() {
        return "CrawlerUrl{" +
                "url='" + url + '\'' +
                ", accepted=" + accepted +
                ", timestamp=" + timestamp +
                ", crawlId='" + crawlId + '\'' +
                ", sourceUrl='" + sourceUrl + '\'' +
                ", sourceXpath='" + sourceXpath + '\'' +
                ", buttonText='" + buttonText + '\'' +
                '}';
    }
}
