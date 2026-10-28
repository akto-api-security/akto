package com.akto.dto.imperva_integration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImpervaIntegration {

    // the Imperva API import is only enabled for this account
    public static final int ENABLED_ACCOUNT_ID = 1745563576;

    public static final String API_ID = "apiId";
    public static final String API_KEY = "apiKey";
    public static final String CREATED_TS = "createdTs";
    public static final String UPDATED_TS = "updatedTs";
    public static final String SYNC_REQUESTED = "syncRequested";
    public static final String LAST_SYNC_START_TS = "lastSyncStartTs";
    public static final String LAST_SYNC_END_TS = "lastSyncEndTs";

    private String apiId;
    private String apiKey;

    private int createdTs;
    private int updatedTs;

    // set from the dashboard, picked up and cleared by the import job
    private boolean syncRequested;
    private int lastSyncStartTs;
    private int lastSyncEndTs;
}
