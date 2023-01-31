package com.akto.dto;

import java.util.Map;

import com.akto.dao.context.Context;
import com.akto.dto.auth.APIAuth;

import org.bson.codecs.pojo.annotations.BsonId;

public class TestEnvSettings {

    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    @BsonId
    int id;

    LogLevel logLevel;
    Map<String, APIAuth> authProtocolSettings;
    int maxRequestsPerSec;
    int maxResponseTime;
    int contentId;

    public TestEnvSettings() {}

    public TestEnvSettings(LogLevel logLevel, Map<String,APIAuth> authProtocolSettings, int maxRequestsPerSec, int maxResponseTime, int contentId) {
        this.id = Context.now();
        this.logLevel = logLevel;
        this.authProtocolSettings = authProtocolSettings;
        this.maxRequestsPerSec = maxRequestsPerSec;
        this.maxResponseTime = maxResponseTime;
        this.contentId = contentId;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public LogLevel getLogLevel() {
        return this.logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public Map<String,APIAuth> getAuthProtocolSettings() {
        return this.authProtocolSettings;
    }

    public void setAuthProtocolSettings(Map<String,APIAuth> authProtocolSettings) {
        this.authProtocolSettings = authProtocolSettings;
    }

    public int getMaxRequestsPerSec() {
        return this.maxRequestsPerSec;
    }

    public void setMaxRequestsPerSec(int maxRequestsPerSec) {
        this.maxRequestsPerSec = maxRequestsPerSec;
    }

    public int getMaxResponseTime() {
        return this.maxResponseTime;
    }

    public void setMaxResponseTime(int maxResponseTime) {
        this.maxResponseTime = maxResponseTime;
    }

    public int getContentId() {
        return this.contentId;
    }

    public void setContentId(int contentId) {
        this.contentId = contentId;
    }
}
