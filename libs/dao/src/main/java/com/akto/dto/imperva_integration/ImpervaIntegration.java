package com.akto.dto.imperva_integration;

import org.bson.types.ObjectId;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ImpervaIntegration {

    public static final String API_ID = "apiId";
    public static final String API_KEY = "apiKey";
    public static final String IMPORT_JOB_ID = "importJobId";
    public static final String HOST_FILTER = "hostFilter";
    public static final String PATH_FILTER = "pathFilter";

    private String apiId;
    private String apiKey;

    // optional comma-separated patterns with * as wildcard; only matching APIs are imported
    private String hostFilter;
    private String pathFilter;

    // the latest import job, used to show its status and to stop it
    private ObjectId importJobId;
}
