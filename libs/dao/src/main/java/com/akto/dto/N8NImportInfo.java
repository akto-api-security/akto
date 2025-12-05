package com.akto.dto;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class N8NImportInfo {

    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_SCHEDULING = "SCHEDULING";
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_FAILED_SCHEDULING = "FAILED_SCHEDULING";

    public static final String TYPE_N8N = "N8N";
    public static final String TYPE_COPILOT_STUDIO = "COPILOT_STUDIO";
    public static final String TYPE_LANGCHAIN = "LANGCHAIN";
    public static final String TYPE_BEDROCK = "BEDROCK";
    public static final String TYPE_AZURE_FOUNDRY = "AZURE_FOUNDRY";

    private ObjectId id;

    @BsonIgnore
    private String hexId;

    private String type; // N8N, COPILOT_STUDIO, LANGCHAIN, BEDROCK, AZURE_FOUNDRY
    private String n8nUrl;
    private String apiKey;
    private String dataIngestionUrl;
    private String dashboardUrl;
    private int createdTimestamp;
    private int updatedTimestamp;
    private String status; // CREATED, SCHEDULING, SCHEDULED, FAILED_SCHEDULING
    private String errorMessage;

    public String getHexId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public N8NImportInfo(String type, String n8nUrl, String apiKey, String dataIngestionUrl, String dashboardUrl,
                         int createdTimestamp, int updatedTimestamp, String status, String errorMessage) {
        this.type = type;
        this.n8nUrl = n8nUrl;
        this.apiKey = apiKey;
        this.dataIngestionUrl = dataIngestionUrl;
        this.dashboardUrl = dashboardUrl;
        this.createdTimestamp = createdTimestamp;
        this.updatedTimestamp = updatedTimestamp;
        this.status = status;
        this.errorMessage = errorMessage;
    }
}
