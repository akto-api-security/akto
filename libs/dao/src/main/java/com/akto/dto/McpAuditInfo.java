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

    public static final String LAST_DETECTED = "lastDetected";
    public static final String MARKED_BY = "markedBy";
    public static final String TYPE = "type";
    public static final String UPDATED_TIMESTAMP = "updatedTimestamp";
    public static final String RESOURCE_NAME = "resourceName";
    public static final String REMARKS = "remarks";
    public static final String API_ACCESS_TYPES = "apiAccessTypes";
    public static final String HOST_COLLECTION_ID = "hostCollectionId";
    public static final String MCP_HOST = "mcpHost";
    public static final String COMPONENT_RISK_ANALYSIS = "componentRiskAnalysis";
    public static final String APPROVAL_CONDITIONS = "approvalConditions";
    public static final String APPROVED_AT = "approvedAt";
    
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