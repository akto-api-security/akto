package com.akto.dto.wiz_integration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WizIntegration {

    // API base URL pattern: https://api.<TENANT_DATA_CENTER>.<ENVIRONMENT>/graphql
    public static final String API_BASE_URL_PATTERN = "https://api.%s.%s/graphql";

    // Field name constants
    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_SECRET = "clientSecret";
    public static final String TENANT_DATA_CENTER = "tenantDataCenter";
    public static final String ACCESS_TOKEN = "accessToken";
    public static final String TOKEN_EXPIRY_TS = "tokenExpiryTs";

    // OAuth 2.0 Client Credentials
    private String clientId;
    private String clientSecret;

    // Tenant configuration
    public  static final String ENVIRONMENT = "wiz.io"; // Fixed environment
    private String tenantDataCenter;  // e.g., "us1", "us2", "eu1", "eu2"

    // Token management for caching
    private String accessToken;
    private long tokenExpiryTs;

    public boolean isTokenValid() {
        return accessToken != null &&
               !accessToken.isEmpty() &&
               System.currentTimeMillis() < tokenExpiryTs;
    }
}
