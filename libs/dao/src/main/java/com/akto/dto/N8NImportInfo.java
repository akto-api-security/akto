package com.akto.dto;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.Map;

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

    public static final String CONFIG_N8N_BASE_URL = "N8N_BASE_URL";
    public static final String CONFIG_N8N_API_KEY = "N8N_API_KEY";
    public static final String CONFIG_DATA_INGESTION_SERVICE_URL = "DATA_INGESTION_SERVICE_URL";

    public static final String CONFIG_LANGSMITH_BASE_URL = "LANGSMITH_BASE_URL";
    public static final String CONFIG_LANGSMITH_API_KEY = "LANGSMITH_API_KEY";

    private ObjectId id;

    @BsonIgnore
    private String hexId;

    private String type; // N8N, COPILOT_STUDIO, LANGCHAIN, BEDROCK, AZURE_FOUNDRY
    private Map<String, String> config; // Key-value pairs for configuration
    private int createdTimestamp;
    private int updatedTimestamp;
    private String status; // CREATED, SCHEDULING, SCHEDULED, FAILED_SCHEDULING
    private String errorMessage;

    public String getHexId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public N8NImportInfo(String type, Map<String, String> config,
                         int createdTimestamp, int updatedTimestamp, String status, String errorMessage) {
        this.type = type;
        this.config = config;
        this.createdTimestamp = createdTimestamp;
        this.updatedTimestamp = updatedTimestamp;
        this.status = status;
        this.errorMessage = errorMessage;
    }
}
