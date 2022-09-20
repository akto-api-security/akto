package com.akto.dto.notifications;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;

import com.akto.dto.type.URLMethods.Method;

public class CustomWebhook {

    @BsonId
    int id;

    String url;
    Map<String, List<String>> headers = new HashMap<>();
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

    public CustomWebhook(int id, String url, Map<String, List<String>> headers, String body, Method method,
            int frequencyInSeconds, String userEmail, int createTime, int lastUpdateTime, int lastSentTimestamp,
            ActiveStatus activeStatus) {
        this.id = id;
        this.url = url;
        this.headers = headers;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
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