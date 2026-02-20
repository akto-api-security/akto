package com.akto.dto;

import java.util.Map;
import java.util.Set;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import com.akto.util.enums.GlobalEnums.Severity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class McpAuditInfo {

    private ObjectId id;

    @BsonIgnore
    private String hexId;
    private int lastDetected;
    private String markedBy;
    private String type;
    private int updatedTimestamp;
    private String resourceName;
    private String remarks;
    private Set<ApiInfo.ApiAccessType> apiAccessTypes;
    private int hostCollectionId;
    
    // Conditional approval fields
    private Map<String, Object> approvalConditions;
    
    // Approval timestamp - set when item is approved or conditionally approved
    private Integer approvedAt;

    private Set<String> flaggedWords;
    private Severity severity;

    public String getHexId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public McpAuditInfo(int lastDetected, String markedBy, String type, int updatedTimestamp, String resourceName, String remarks, Set<ApiInfo.ApiAccessType> apiAccessTypes, int hostCollectionId, Set<String> flaggedWords) {
        this.lastDetected = lastDetected;
        this.markedBy = markedBy;
        this.type = type;
        this.updatedTimestamp = updatedTimestamp;
        this.resourceName = resourceName;
        this.remarks = remarks;
        this.apiAccessTypes = apiAccessTypes;
        this.hostCollectionId = hostCollectionId;
        this.flaggedWords = flaggedWords;
        this.severity = (flaggedWords != null && !flaggedWords.isEmpty()) ? Severity.HIGH : null;
    }
}
