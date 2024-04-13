package com.akto.dto;

import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;

import com.akto.dao.context.Context;

public class TestRun {

    public static enum TestRunStatus {
        INIT, STARTED, COMPLETED, FAILED;
    }

    @BsonId
    int id;
    int apiSpecId;
    int testEnvSettingsId;
    String testRunStatus;
    List<String> attempts;

    public TestRun() {
    }

    public TestRun(int apiSpecId, int testEnvSettingsId, List<String> attempts) {
        this.id = Context.now();
        this.apiSpecId = apiSpecId;
        this.testEnvSettingsId = testEnvSettingsId;
        this.attempts = attempts;
        this.testRunStatus = TestRunStatus.INIT.toString();
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getApiSpecId() {
        return this.apiSpecId;
    }

    public void setApiSpecId(int apiSpecId) {
        this.apiSpecId = apiSpecId;
    }

    public int getTestEnvSettingsId() {
        return this.testEnvSettingsId;
    }

    public void setTestEnvSettingsId(int testEnvSettingsId) {
        this.testEnvSettingsId = testEnvSettingsId;
    }

    public List<String> getAttempts() {
        return this.attempts;
    }

    public void setAttempts(List<String> attempts) {
        this.attempts = attempts;
    }

    public String getTestRunStatus() {
        return this.testRunStatus;
    }

    public void setTestRunStatus(String testRunStatus) {
        this.testRunStatus = testRunStatus;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", apiSpecId='" + getApiSpecId() + "'" +
            ", testEnvSettingsId='" + getTestEnvSettingsId() + "'" +
            ", attemps='" + getAttempts() + "'" +
            ", testRunStatus='" + getTestRunStatus() + "'" +
            "}";
    }
    
}
