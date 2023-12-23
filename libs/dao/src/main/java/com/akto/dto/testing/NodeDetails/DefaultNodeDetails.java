package com.akto.dto.testing.NodeDetails;

import com.akto.dto.testing.WorkflowUpdatedSampleData;
import com.akto.dto.type.URLMethods.Method;

public class DefaultNodeDetails extends NodeDetails {
    
    int apiCollectionId;
    String endpoint;
    Method method;
    WorkflowUpdatedSampleData updatedSampleData;

    boolean overrideRedirect;
    String testValidatorCode;

    int waitInSeconds;

    int maxPollRetries;

    int pollRetryDuration;

    String otpRegex;

    String otpRefUuid;

    public DefaultNodeDetails(int apiCollectionId, String endpoint, Method method, String testValidatorCode,
    WorkflowUpdatedSampleData updatedSampleData, boolean overrideRedirect,
    int waitInSeconds, int maxPollRetries, int pollRetryDuration, String otpRegex, String otpRefUuid) {
        this.apiCollectionId = apiCollectionId;
        this.endpoint = endpoint;
        this.method = method;
        this.updatedSampleData = updatedSampleData;
        this.overrideRedirect = overrideRedirect;
        this.testValidatorCode = testValidatorCode;
        this.waitInSeconds = waitInSeconds;
        this.maxPollRetries = maxPollRetries;
        this.pollRetryDuration = pollRetryDuration;
        this.otpRegex = otpRegex;
        this.otpRefUuid = otpRefUuid;
    }

    public DefaultNodeDetails() {
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public WorkflowUpdatedSampleData getUpdatedSampleData() {
        return updatedSampleData;
    }

    public void setUpdatedSampleData(WorkflowUpdatedSampleData updatedSampleData) {
        this.updatedSampleData = updatedSampleData;
    }

    public boolean getOverrideRedirect() {
        return overrideRedirect;
    }

    public void setOverrideRedirect(boolean overrideRedirect) {
        this.overrideRedirect = overrideRedirect;
    }

    public String getTestValidatorCode() {
        return testValidatorCode;
    }

    public void setTestValidatorCode(String testValidatorCode) {
        this.testValidatorCode = testValidatorCode;
    }

    public int getWaitInSeconds() {
        return waitInSeconds;
    }

    public void setWaitInSeconds(int waitInSeconds) {
        this.waitInSeconds = waitInSeconds;
    }

    public int getMaxPollRetries() {
        return maxPollRetries;
    }

    public void setMaxPollRetries(int maxPollRetries) {
        this.maxPollRetries = maxPollRetries;
    }

    public int getPollRetryDuration() {
        return pollRetryDuration;
    }

    public void setPollRetryDuration(int pollRetryDuration) {
        this.pollRetryDuration = pollRetryDuration;
    }

    public String getOtpRegex() {
        return otpRegex;
    }

    public void setOtpRegex(String otpRegex) {
        this.otpRegex = otpRegex;
    }

    public String getOtpRefUuid() {
        return otpRefUuid;
    }

    public void setOtpRefUuid(String otpRefUuid) {
        this.otpRefUuid = otpRefUuid;
    }

    public String validate() {
        //if (this.type == null ) return "Type can't be null";
        if (this.endpoint == null ) return "URL can't be null";
        if (this.method == null ) return "Method can't be null";
        int waitThreshold = 60;
        if (waitInSeconds > waitThreshold) return "Wait time should be <= " + waitThreshold;

        return null;
    }

}
