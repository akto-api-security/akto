package com.akto.dto.devrev_integration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DevRevIntegration {

    public static final String API_BASE_URL = "https://api.devrev.ai";

    public static final String ORG_URL = "orgUrl";
    public static final String PERSONAL_ACCESS_TOKEN = "personalAccessToken";
    public static final String PARTS_ID_TO_NAME_MAP = "partsMap";
    public static final String CREATED_TS = "createdTs";
    public static final String UPDATED_TS = "updatedTs";

    private String orgUrl;
    private String personalAccessToken;
    private Map<String, String> partsMap;
    private int createdTs;
    private int updatedTs;
}