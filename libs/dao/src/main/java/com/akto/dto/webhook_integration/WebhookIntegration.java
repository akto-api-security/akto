package com.akto.dto.webhook_integration;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class WebhookIntegration {

    public static final String URL = "url";
    private String url;

    public static final String CUSTOM_HEADERS = "customHeaders";
    private Map<String, String> customHeaders;

    public static final String CONTEXT_SOURCES = "contextSources";
    private List<String> contextSources;

    public static final String LAST_SYNC_TIME = "lastSyncTime";
    private int lastSyncTime;

    public WebhookIntegration() {}

    public WebhookIntegration(String url, Map<String, String> customHeaders, List<String> contextSources, int lastSyncTime) {
        this.url = url;
        this.customHeaders = customHeaders;
        this.contextSources = contextSources;
        this.lastSyncTime = lastSyncTime;
    }
}
