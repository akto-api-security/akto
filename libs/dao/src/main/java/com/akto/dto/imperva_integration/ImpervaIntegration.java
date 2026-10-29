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

    private String apiId;
    private String apiKey;

    // the latest import job, used to show its status and to stop it
    private ObjectId importJobId;
}
