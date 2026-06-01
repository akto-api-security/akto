package com.akto.dto.open_telemetry_integration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpenTelemetryIntegration {

    public static final String ENDPOINT = "endpoint";
    public static final String API_KEY = "apiKey";
    public static final String CREATED_TS = "createdTs";
    public static final String UPDATED_TS = "updatedTs";

    private String endpoint;
    private String apiKey;

    // audit fields
    private int createdTs;
    private int updatedTs;
}
