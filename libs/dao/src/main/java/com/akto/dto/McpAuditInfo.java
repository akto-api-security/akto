package com.akto.dto;

import com.mongodb.BasicDBObject;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.Set;

public class McpAuditInfo {

    @BsonId
    int id;
    public static final String ID = "_id";
    private String lastDetected;
    private String markedBy;
    private String type;
    private String updatedTimestamp;
    private String resourceName;
    private String remarks;
    private Set<ApiInfo.ApiAccessType> apiAccessTypes;

    public enum ApiAccessType {
        PUBLIC, PRIVATE, PARTNER, THIRD_PARTY
    }

    public McpAuditInfo() {}

    public McpAuditInfo(String lastDetected, String markedBy, String type, String updatedTimestamp, String resourceName, String remarks, Set<ApiInfo.ApiAccessType> apiAccessTypes) {
        this.lastDetected = lastDetected;
        this.markedBy = markedBy;
        this.type = type;
        this.updatedTimestamp = updatedTimestamp;
        this.resourceName = resourceName;
        this.remarks = remarks;
        this.apiAccessTypes = apiAccessTypes;
    }

    public String getLastDetected() {
        return lastDetected;
    }

    public void setLastDetected(String lastDetected) {
        this.lastDetected = lastDetected;
    }

    public String getMarkedBy() {
        return markedBy;
    }

    public void setMarkedBy(String markedBy) {
        this.markedBy = markedBy;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUpdatedTimestamp() {
        return updatedTimestamp;
    }

    public void setUpdatedTimestamp(String updatedTimestamp) {
        this.updatedTimestamp = updatedTimestamp;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Set<ApiInfo.ApiAccessType> getApiAccessTypes() {
        return apiAccessTypes;
    }

    public void setApiAccessTypes(Set<ApiInfo.ApiAccessType> apiAccessTypes) {
        this.apiAccessTypes = apiAccessTypes;
    }
}
