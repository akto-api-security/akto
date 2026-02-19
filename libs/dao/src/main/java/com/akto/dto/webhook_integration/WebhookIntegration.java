package com.akto.dto.webhook_integration;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class WebhookIntegration {

    public static final String URL = "url";
    private String url;

    public static final String CUSTOM_HEADERS = "customHeaders";
    private Map<String, String> customHeaders;

    public static final String CONTEXT_SOURCE = "contextSource";
    private String contextSource;

    public static final String LAST_SYNC_TIME = "lastSyncTime";
    private int lastSyncTime;

    public static final String USE_GZIP = "useGzip";
    private boolean useGzip;

    public WebhookIntegration() {}

    public WebhookIntegration(String url, Map<String, String> customHeaders, String contextSource, int lastSyncTime, boolean useGzip) {
        this.url = url;
        this.customHeaders = customHeaders;
        this.contextSource = contextSource;
        this.lastSyncTime = lastSyncTime;
        this.useGzip = useGzip;
    }
}
