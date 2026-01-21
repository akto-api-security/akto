package com.akto.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

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

    public static final String STATUS = "status";
    private CrawlerRunStatus status;

    public static final String MODULE_NAME = "moduleName";
    private String moduleName;

    public static final String USERNAME = "username";
    private String username;

    public static final String PASSWORD = "password";
    private String password;

    public static final String API_KEY = "apiKey";
    private String apiKey;

    public static final String DASHBOARD_URL = "dashboardUrl";
    private String dashboardUrl;

    public static final String COLLECTION_ID = "collectionId";
    private Integer collectionId;

    public static final String ACCOUNT_ID = "accountId";
    private Integer accountId;

    public static final String ERROR_MESSAGE = "errorMessage";
    private String errorMessage;

    public static final String COOKIES = "cookies";
    private Object cookies;

    public static final String CRAWLING_TIME = "crawlingTime";
    private Integer crawlingTime;

    public static final String CUSTOM_HEADERS = "customHeaders";
    private Map<String, String> customHeaders;

    public static final String RUN_TEST_AFTER_CRAWLING = "runTestAfterCrawling";
    private boolean runTestAfterCrawling;

    public static final String SELECTED_MINI_TESTING_SERVICE = "selectedMiniTestingService";
    private String selectedMiniTestingService;

    public CrawlerRun() {
    }

    public CrawlerRun(String startedBy, int startTimestamp, int endTimestamp, String crawlId, String hostname, String outScopeUrls, boolean runTestAfterCrawling) {
        this.startedBy = startedBy;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.crawlId = crawlId;
        this.hostname = hostname;
        this.outScopeUrls = outScopeUrls;
        this.runTestAfterCrawling = runTestAfterCrawling;
    }

    public enum CrawlerRunStatus {
        PENDING, RUNNING, COMPLETED, FAILED
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
                ", status='" + status.name() + '\'' +
                ", moduleName='" + moduleName + '\'' +
                ", runTestAfterCrawling='" + runTestAfterCrawling + '\'' +
                '}';
    }
}
