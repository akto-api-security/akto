package com.akto.dto.notifications;

import org.bson.codecs.pojo.annotations.BsonId;

import com.akto.dto.type.URLMethods.Method;

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
    public enum ActiveStatus{
        ACTIVE,INACTIVE;
    }
    ActiveStatus activeStatus;

    public CustomWebhook(){

    }

    public CustomWebhook(int id, String webhookName, String url, String headerString, String queryParams, String body,
            Method method, int frequencyInSeconds, String userEmail, int createTime, int lastUpdateTime,
            int lastSentTimestamp, ActiveStatus activeStatus) {
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