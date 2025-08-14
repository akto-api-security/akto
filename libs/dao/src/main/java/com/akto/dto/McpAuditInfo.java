package com.akto.dto;

import java.util.Set;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class McpAuditInfo {

    private ObjectId id;

    @BsonIgnore
    private String hexId;
    private String lastDetected;
    private String markedBy;
    private String type;
    private String updatedTimestamp;
    private String resourceName;
    private String remarks;
    private Set<ApiInfo.ApiAccessType> apiAccessTypes;

    public String getHexId() {
        return this.id.toHexString();
    }

    public McpAuditInfo(String lastDetected, String markedBy, String type, String updatedTimestamp, String resourceName, String remarks, Set<ApiInfo.ApiAccessType> apiAccessTypes) {
        this.lastDetected = lastDetected;
        this.markedBy = markedBy;
        this.type = type;
        this.updatedTimestamp = updatedTimestamp;
        this.resourceName = resourceName;
        this.remarks = remarks;
        this.apiAccessTypes = apiAccessTypes;
    }

}
