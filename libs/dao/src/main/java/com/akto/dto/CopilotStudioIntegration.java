package com.akto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks a customer's Copilot Studio (Multi Environment) connector setup end-to-end:
 * from credential submission, through OAuth registration and environment discovery,
 * to the ongoing per-environment app-user/ingestion state the recurring job maintains.
 */
@Getter
@Setter
@NoArgsConstructor
public class CopilotStudioIntegration {

    public static final String ID = "_id";
    public static final String STATUS = "status";
    public static final String ENVIRONMENTS = "environments";
    public static final String JOB_ID = "jobId";
    public static final String LAST_ERROR = "lastError";
    public static final String UPDATED_AT = "updatedAt";

    public enum Status {
        PENDING_OAUTH,
        ENVIRONMENTS_DISCOVERED,
        CONFIRMED
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Environment {
        private String environmentId;
        private String environmentUrl;
        private String environmentName;
        private boolean appUserCreated;
        private int lastIngestedAt;
        private String lastError;

        public Environment(String environmentId, String environmentUrl, String environmentName) {
            this.environmentId = environmentId;
            this.environmentUrl = environmentUrl;
            this.environmentName = environmentName;
        }
    }

    @JsonIgnore
    private ObjectId id;
    @BsonIgnore
    private String hexId;
    private String tenantId;
    private String clientId;
    private String clientSecret;
    private String dataIngestionUrl;
    private Status status;
    private List<Environment> environments = new ArrayList<>();
    private String jobId;
    private String lastError;
    private int createdAt;
    private int updatedAt;

    public CopilotStudioIntegration(String tenantId, String clientId, String clientSecret, String dataIngestionUrl, int now) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.dataIngestionUrl = dataIngestionUrl;
        this.status = Status.PENDING_OAUTH;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getHexId() {
        return this.id.toHexString();
    }
}
