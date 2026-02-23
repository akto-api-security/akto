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
    private int lastDetected;
    private String markedBy;
    private String type;
    private int updatedTimestamp;
    private String resourceName;
    private String remarks;
    private Set<ApiInfo.ApiAccessType> apiAccessTypes;
    private int hostCollectionId;
    private String mcpHost;
    private ComponentRiskAnalysis componentRiskAnalysis;

    public String getHexId() {
        return this.id.toHexString();
    }
    public McpAuditInfo(int lastDetected,
        String markedBy,
        String type,
        int updatedTimestamp,
        String resourceName,
        String remarks,
        Set<ApiInfo.ApiAccessType> apiAccessTypes,
        int hostCollectionId,
        String mcpHost) {
        this.lastDetected = lastDetected;
        this.markedBy = markedBy;
        this.type = type;
        this.updatedTimestamp = updatedTimestamp;
        this.resourceName = resourceName;
        this.remarks = remarks;
        this.apiAccessTypes = apiAccessTypes;
        this.hostCollectionId = hostCollectionId;
        this.mcpHost = mcpHost;
    }
}