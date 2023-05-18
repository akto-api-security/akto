package com.akto.dto.notifications;

import com.akto.dto.data_types.Conditions;
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
    public static final String NEW_ENDPOINT_COLLECTIONS = "newEndpointCollections";
    private List<String> newEndpointCollections;
    public static final String NEW_SENSITIVE_ENDPOINT_COLLECTIONS = "newSensitiveEndpointCollections";
    private List<String> newSensitiveEndpointCollections;
//    public static final String NEW_ENDPOINT_CONDITIONS = "newEndpointConditions";
//    private Conditions newEndpointConditions;
//    public static final String NEW_SENSITIVE_ENDPOINT_CONDITIONS = "newSensitiveEndpointConditions";
//    private Conditions newSensitiveEndpointConditions;
    public static final String SELECTED_WEBHOOK_OPTIONS = "selectedWebhookOptions";
    private List<WebhookOptions> selectedWebhookOptions;

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

    public enum ActiveStatus{
        ACTIVE,INACTIVE;
    }

    public enum WebhookOptions {
        NEW_ENDPOINT ("New Endpoint", "${AKTO.changes_info.newEndpoints}"),
        NEW_ENDPOINT_COUNT("New Endpoint Count", "${AKTO.changes_info.newEndpointsCount}"),
        NEW_SENSITIVE_ENDPOINT ("New Sensitive Endpoint", "${AKTO.changes_info.newSensitiveEndpoints}"),
        NEW_SENSITIVE_ENDPOINT_COUNT("New Sensitive Endpoint Count", "${AKTO.changes_info.newSensitiveEndpointsCount}"),
        NEW_PARAMETER_COUNT("New Parameter Count", "${AKTO.changes_info.newParametersCount}"),
        NEW_SENSITIVE_PARAMETER_COUNT("New Sensitive Parameter Count", "${AKTO.changes_info.newSensitiveParametersCount}");

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
//        this.newEndpointConditions = newEndpointConditions;
//        this.newSensitiveEndpointConditions = newSensitiveEndpointConditions;
        this.newEndpointCollections = newEndpointCollections;
        this.newSensitiveEndpointCollections = newSensitiveEndpointCollections;
    }
//    public Conditions getNewEndpointConditions() {
//        return newEndpointConditions;
//    }
//
//    public void setNewEndpointConditions(Conditions newEndpointConditions) {
//        this.newEndpointConditions = newEndpointConditions;
//    }
//
//    public Conditions getNewSensitiveEndpointConditions() {
//        return newSensitiveEndpointConditions;
//    }
//
//    public void setNewSensitiveEndpointConditions(Conditions newSensitiveEndpointConditions) {
//        this.newSensitiveEndpointConditions = newSensitiveEndpointConditions;
//    }

    public List<WebhookOptions> getSelectedWebhookOptions() {
        return selectedWebhookOptions;
    }

    public void setSelectedWebhookOptions(List<WebhookOptions> selectedWebhookOptions) {
        this.selectedWebhookOptions = selectedWebhookOptions;
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
    
}