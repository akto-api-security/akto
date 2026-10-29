package com.akto.dto.imperva_integration;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ImpervaIntegration {

    public static final String API_ID = "apiId";
    public static final String API_KEY = "apiKey";
    public static final String SYNC_REQUESTED = "syncRequested";
    public static final String LAST_SYNC_START_TS = "lastSyncStartTs";
    public static final String LAST_SYNC_END_TS = "lastSyncEndTs";

    private String apiId;
    private String apiKey;

    // set when an import is triggered from the dashboard, cleared once the import job finishes
    private boolean syncRequested;
    private int lastSyncStartTs;
    private int lastSyncEndTs;
}
