package com.akto.dto.audit_logs;

import java.util.List;

import com.mongodb.BasicDBObject;

import lombok.Getter;
import lombok.Setter;

public class ApiAuditLogs {

    public static final String TIMESTAMP = "timestamp";
    private long timestamp;

    public static final String API_ENDPOINT = "apiEndpoint";
    private String apiEndpoint;

    public static final String ACTION_DESCRIPTION = "actionDescription";
    private String actionDescription;

    public static final String USER_EMAIL = "userEmail";
    private String userEmail;

    public static final String USER_AGENT = "userAgent";
    private String userAgent;

    public static final String USER_IP_ADDRESS = "userIpAddress";
    private String userIpAddress;

    public static final String USER_PROXY_IP_ADDRESSES = "userProxyIpAddresses";
    private List<String> userProxyIpAddresses;

    public static final String RESOURCE = "resource";
    @Getter
    @Setter
    private Resource resource = Resource.NOT_SPECIFIED;

    public static final String OPERATION = "operation";
    @Getter
    @Setter
    private Operation operation = Operation.NOT_SPECIFIED;

    public static final String METADATA = "metadata";
    @Getter
    @Setter
    private BasicDBObject metadata = new BasicDBObject();

    public ApiAuditLogs() {}

    public ApiAuditLogs(long timestamp, String apiEndpoint, String actionDescription, String userEmail, String userAgent, String userIpAddress, List<String> userProxyIpAddresses, Resource resource, Operation operation, BasicDBObject metadata) {
        this.timestamp = timestamp;
        this.apiEndpoint = apiEndpoint;
        this.actionDescription = actionDescription;
        this.userEmail = userEmail;
        this.userAgent = userAgent;
        this.userIpAddress = userIpAddress;
        this.userProxyIpAddresses = userProxyIpAddresses;
        this.resource = resource;
        this.operation = operation;
        this.metadata = metadata;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    public String getActionDescription() {
        return actionDescription;
    }

    public void setActionDescription(String actionDescription) {
        this.actionDescription = actionDescription;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getUserIpAddress() {
        return userIpAddress;
    }

    public void setUserIpAddress(String userIpAddress) {
        this.userIpAddress = userIpAddress;
    }

    public List<String> getUserProxyIpAddresses() {
        return userProxyIpAddresses;
    }

    public void setUserProxyIpAddresses(List<String> userProxyIpAddresses) {
        this.userProxyIpAddresses = userProxyIpAddresses;
    }
}
