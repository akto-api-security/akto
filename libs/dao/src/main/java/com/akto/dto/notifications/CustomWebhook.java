package com.akto.dto.notifications;

import org.bson.codecs.pojo.annotations.BsonId;

import com.akto.dto.type.URLMethods.Method;

import java.util.List;

public class CustomWebhook {

    @BsonId
    int id;
    String webhookName;
    String url;
    String headerString;
    String queryParams;
    String body;
    Method method;
    int frequencyInSeconds;
    String userEmail;
    int createTime;
    int lastUpdateTime;
    int lastSentTimestamp;
    ActiveStatus activeStatus;
    final public static String BATCH_SIZE = "batchSize";
    int batchSize;
    public static final String NEW_ENDPOINT_COLLECTIONS = "newEndpointCollections";
    private List<String> newEndpointCollections;
    public static final String NEW_SENSITIVE_ENDPOINT_COLLECTIONS = "newSensitiveEndpointCollections";
    private List<String> newSensitiveEndpointCollections;
    public static final String SELECTED_WEBHOOK_OPTIONS = "selectedWebhookOptions";
    private List<WebhookOptions> selectedWebhookOptions;
    public static final String SEND_INSTANTLY = "sendInstantly";
    private boolean sendInstantly;

    private String dashboardUrl;
    public static final String DASHBOARD_URL = "dashboardUrl";

    private WebhookType webhookType;
    public static final String WEBHOOK_TYPE = "webhookType";

    public enum WebhookType {
        DEFAULT, MICROSOFT_TEAMS, GMAIL
    }

    public enum ActiveStatus{
        ACTIVE,INACTIVE;
    }

    public enum WebhookOptions {
        NEW_ENDPOINT ("New Endpoint", "${AKTO.changes_info.newEndpoints}"),
        NEW_ENDPOINT_COUNT("New Endpoint Count", "${AKTO.changes_info.newEndpointsCount}"),
        NEW_SENSITIVE_ENDPOINT ("New Sensitive Endpoint", "${AKTO.changes_info.newSensitiveEndpoints}"),
        NEW_SENSITIVE_ENDPOINT_COUNT("New Sensitive Endpoint Count", "${AKTO.changes_info.newSensitiveEndpointsCount}"),
        NEW_PARAMETER_COUNT("New Parameter Count", "${AKTO.changes_info.newParametersCount}"),
        NEW_SENSITIVE_PARAMETER_COUNT("New Sensitive Parameter Count", "${AKTO.changes_info.newSensitiveParametersCount}"),
        API_THREAT_PAYLOADS("API Threat payloads", "${AKTO.changes_info.apiThreatPayloads}"),
        // optionReplaceString not being used for Testing Run results.
        TESTING_RUN_RESULTS("Testing run results", "${AKTO.changes_info.apiTestingRunResults}"),
        // optionReplaceString not being used for Traffic alerts.
        TRAFFIC_ALERTS("Traffic alerts", "${AKTO.changes_info.apiTrafficAlerts}"),
        PENDING_TESTS_ALERTS("Pending tests alerts", "${AKTO.changes_info.apiPendingTestsAlerts}");

        final String optionName;
        final String optionReplaceString;
        WebhookOptions(String webhookName, String optionReplaceString){
            this.optionName = webhookName;
            this.optionReplaceString = optionReplaceString;
        }
        public String getOptionName() {
            return optionName;
        }
        public String getOptionReplaceString() {
            return optionReplaceString;
        }
    }
    public CustomWebhook(){}

    public CustomWebhook(int id, String webhookName, String url, String headerString, String queryParams, String body,
                         Method method, int frequencyInSeconds, String userEmail, int createTime, int lastUpdateTime,
                         int lastSentTimestamp, ActiveStatus activeStatus, List<WebhookOptions> selectedWebhookOptions,
                         List<String> newEndpointCollections, List<String> newSensitiveEndpointCollections) {
        this.id = id;
        this.webhookName = webhookName;
        this.url = url;
        this.headerString = headerString;
        this.queryParams = queryParams;
        this.body = body;
        this.method = method;
        this.frequencyInSeconds = frequencyInSeconds;
        this.userEmail = userEmail;
        this.createTime = createTime;
        this.lastUpdateTime = lastUpdateTime;
        this.lastSentTimestamp = lastSentTimestamp;
        this.activeStatus = activeStatus;
        this.selectedWebhookOptions = selectedWebhookOptions;
        this.newEndpointCollections = newEndpointCollections;
        this.newSensitiveEndpointCollections = newSensitiveEndpointCollections;
    }
    public List<WebhookOptions> getSelectedWebhookOptions() {
        return selectedWebhookOptions;
    }

    public void setSelectedWebhookOptions(List<WebhookOptions> selectedWebhookOptions) {
        this.selectedWebhookOptions = selectedWebhookOptions;
    }

    public List<String> getNewEndpointCollections() {
        return newEndpointCollections;
    }

    public void setNewEndpointCollections(List<String> newEndpointCollections) {
        this.newEndpointCollections = newEndpointCollections;
    }

    public List<String> getNewSensitiveEndpointCollections() {
        return newSensitiveEndpointCollections;
    }

    public void setNewSensitiveEndpointCollections(List<String> newSensitiveEndpointCollections) {
        this.newSensitiveEndpointCollections = newSensitiveEndpointCollections;
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getWebhookName() {
        return webhookName;
    }

    public void setWebhookName(String webhookName) {
        this.webhookName = webhookName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHeaderString() {
        return headerString;
    }

    public void setHeaderString(String headerString) {
        this.headerString = headerString;
    }

    public String getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(String queryParams) {
        this.queryParams = queryParams;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public int getFrequencyInSeconds() {
        return frequencyInSeconds;
    }

    public void setFrequencyInSeconds(int frequencyInSeconds) {
        this.frequencyInSeconds = frequencyInSeconds;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public int getCreateTime() {
        return createTime;
    }

    public void setCreateTime(int createTime) {
        this.createTime = createTime;
    }

    public int getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(int lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public int getLastSentTimestamp() {
        return lastSentTimestamp;
    }

    public void setLastSentTimestamp(int lastSentTimestamp) {
        this.lastSentTimestamp = lastSentTimestamp;
    }

    public ActiveStatus getActiveStatus() {
        return activeStatus;
    }

    public void setActiveStatus(ActiveStatus activeStatus) {
        this.activeStatus = activeStatus;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public WebhookType getWebhookType() {
        return webhookType;
    }

    public void setWebhookType(WebhookType webhookType) {
        this.webhookType = webhookType;
    }
    
    public boolean getSendInstantly() {
        return sendInstantly;
    }

    public void setSendInstantly(boolean sendInstantly) {
        this.sendInstantly = sendInstantly;
    }

    public String getDashboardUrl() {
        return dashboardUrl;
    }

    public void setDashboardUrl(String dashboardUrl) {
        this.dashboardUrl = dashboardUrl;
    }
}