package com.akto.dto.new_relic_integration;

import org.bson.types.ObjectId;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NewRelicIntegration {

    // Field name constants
    public static final String API_KEY = "apiKey";

    public static final String CREATED_TS = "createdTs";
    public static final String UPDATED_TS = "updatedTs";

    private String apiKey;

    // audit fields
    private int createdTs;
    private int updatedTs;
    
}