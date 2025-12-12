package com.akto.dto.jobs;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.bson.types.ObjectId;

import java.util.Map;

/**
 * Generic DTO for account-level jobs.
 * Can be used by any feature that needs account-level job tracking
 * (AI Agent Connectors, scheduled reports, data exports, etc.).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AccountJob {

    // Field name constants
    public static final String ID = "_id";
    public static final String ACCOUNT_ID = "accountId";
    public static final String JOB_TYPE = "jobType";
    public static final String SUB_TYPE = "subType";
    public static final String CONFIG = "config";
    public static final String RECURRING_INTERVAL_SECONDS = "recurringIntervalSeconds";
    public static final String CREATED_AT = "createdAt";
    public static final String LAST_UPDATED_AT = "lastUpdatedAt";

    // Fields
    private ObjectId id;                        // Primary key
    private int accountId;                      // Account identifier
    private String jobType;                     // Generic job type (e.g., "AI_AGENT_CONNECTOR", "SCHEDULED_REPORT")
    private String subType;                     // Job sub-type (e.g., "N8N", "LANGCHAIN", "COPILOT_STUDIO")
    private Map<String, Object> config;         // Flexible configuration (any data structure)
    private int recurringIntervalSeconds;       // Recurrence interval (0 for non-recurring)
    private int createdAt;                      // Creation timestamp
    private int lastUpdatedAt;                  // Last update timestamp

    /**
     * Constructor without id field (MongoDB will auto-generate the id).
     * Use this constructor when creating new AccountJob instances.
     */
    public AccountJob(int accountId, String jobType, String subType, Map<String, Object> config,
                      int recurringIntervalSeconds, int createdAt, int lastUpdatedAt) {
        this.accountId = accountId;
        this.jobType = jobType;
        this.subType = subType;
        this.config = config;
        this.recurringIntervalSeconds = recurringIntervalSeconds;
        this.createdAt = createdAt;
        this.lastUpdatedAt = lastUpdatedAt;
    }
}
