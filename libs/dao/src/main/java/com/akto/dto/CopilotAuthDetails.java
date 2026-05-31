package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CopilotAuthDetails {
    
    public static final String REFRESH_TOKEN = "refreshToken";

    private String tenantId;
    private String clientId;
    private String clientSecret;
    private String refreshToken;
}
