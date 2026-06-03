package com.akto.dto;

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
    public static final String HEADER_NAME = "headerName";
    public static final String CREATED_TS = "createdTs";
    public static final String UPDATED_TS = "updatedTs";

    private String endpoint;
    private String apiKey;
    private String headerName;

    // audit fields
    private int createdTs;
    private int updatedTs;
}