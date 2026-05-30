package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CopilotAuthDetails {
    private String tenantId;
    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String environmentId;
}
