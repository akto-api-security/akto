package com.akto.dto.wiz_integration;

import org.bson.types.ObjectId;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WizIntegration {

    public enum FindingCreationStatus {
        CREATION_REQUESTED,
        CREATION_INITIATED,
        CREATION_SUCCESSFUL,
        CREATION_FAILED
    }

    // API base URL pattern: https://api.<TENANT_DATA_CENTER>.<ENVIRONMENT>/graphql
    public static final String API_BASE_URL_PATTERN = "https://api.%s.%s/graphql";

    // Field name constants
    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_SECRET = "clientSecret";
    public static final String TENANT_DATA_CENTER = "tenantDataCenter";
    public static final String ACCESS_TOKEN = "accessToken";
    public static final String TOKEN_EXPIRY_TS = "tokenExpiryTs";
    public static final String CREATED_TS = "createdTs";
    public static final String UPDATED_TS = "updatedTs";
    public static final String SYSTEM_ACTIVITY_ID = "systemActivityId";
    public static final String LAST_UPLOADED_SCAN_TS = "lastUploadedScanTs";
    public static final String WIZ_SYNC_JOB_ID = "wizSyncJobId";
    public static final String WIZ_IMPORT_API_ENDPOINTS_JOB_ID = "wizImportApiEndpointsJobId";


    // OAuth 2.0 Client Credentials
    private String clientId;
    private String clientSecret;

    // Tenant configuration
    public  static final String ENVIRONMENT = "app.wiz.io"; // Fixed environment
    private String tenantDataCenter;  // e.g., "us1", "us2", "eu1", "eu2"

    // Token management for caching
    private String accessToken;
    private long tokenExpiryTs;

    // audit fields
    private int createdTs;
    private int updatedTs;

    // System action status (for tracking status of uploaded security scans)
    private String systemActivityId;
    private long lastUploadedScanTs;

    // Jobs
    private ObjectId wizSyncJobId;
    private ObjectId wizImportApiEndpointsJobId;

    public boolean isTokenValid() {
        return accessToken != null &&
               !accessToken.isEmpty() &&
               System.currentTimeMillis() < tokenExpiryTs;
    }
}
