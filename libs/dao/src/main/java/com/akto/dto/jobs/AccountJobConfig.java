package com.akto.dto.jobs;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

/**
 * Generic per-account config/state store: one document per configKey.
 * Distinct from AccountJob (which tracks job execution — status/scheduledAt/heartbeat),
 * this is for arbitrary config/state blobs any feature needs to persist per account
 * (e.g. Cyborg's compliance cursor-sync state).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AccountJobConfig {

    public static final String CONFIG_KEY = "configKey";
    public static final String CONFIG = "config";
    public static final String CREATED_AT = "createdAt";
    public static final String LAST_UPDATED_AT = "lastUpdatedAt";
                      
    private String configKey;                   // Identifies which feature this doc belongs to (e.g. "CYBORG_COMPLIANCE_STATE")
    private Map<String, Object> config;          // Arbitrary state payload, feature-defined shape
    private int createdAt;                       // Creation timestamp
    private int lastUpdatedAt;                   // Last update timestamp

    
}
