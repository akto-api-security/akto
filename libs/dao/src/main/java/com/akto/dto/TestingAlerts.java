package com.akto.dto;

import org.bson.types.ObjectId;

public class TestingAlerts {

    private ObjectId id;
    private int accountId;
    private ObjectId testRunId;
    private String status;
    private int updatedTs;
    private boolean alertSent;

    public TestingAlerts() {
    }

    public TestingAlerts(int accountId, ObjectId testRunId, String status, int updatedTs, boolean alertSent) {
        this.accountId = accountId;
        this.testRunId = testRunId;
        this.status = status;
        this.updatedTs = updatedTs;
        this.alertSent = alertSent;
    }
    
    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public ObjectId getTestRunId() {
        return testRunId;
    }

    public void setTestRunId(ObjectId testRunId) {
        this.testRunId = testRunId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getUpdatedTs() {
        return updatedTs;
    }

    public void setUpdatedTs(int updatedTs) {
        this.updatedTs = updatedTs;
    }

    public boolean isAlertSent() {
        return alertSent;
    }

    public void setAlertSent(boolean alertSent) {
        this.alertSent = alertSent;
    }
    
}
