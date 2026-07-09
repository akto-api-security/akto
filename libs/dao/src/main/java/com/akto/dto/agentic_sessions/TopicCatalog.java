package com.akto.dto.agentic_sessions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TopicCatalog {

    public static final String NAME             = "name";
    public static final String DESCRIPTION     = "description";
    public static final String SAMPLE_PHRASES  = "samplePhrases";
    public static final String CREATED_AT      = "createdAt";
    public static final String UPDATED_AT      = "updatedAt";

    // Field named "id" maps to MongoDB _id by convention.
    // Hash of the (sanitized) topic name — this IS the dedup key.
    private String id;

    // Human-readable topic name, e.g. "technology" — the source the id was hashed from.
    private String name;

    private String description = "";
    private List<String> samplePhrases = new ArrayList<>();
    private long createdAt;
    private long updatedAt;
}
