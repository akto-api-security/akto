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
public class QueryTopicCache {

    public static final String TOPICS           = "topics";
    public static final String HARMFUL          = "harmful";
    public static final String HARMFUL_CATEGORY = "harmfulCategory";
    public static final String HARMFUL_REASON   = "harmfulReason";
    public static final String CREATED_AT       = "createdAt";

    // Field named "id" maps to MongoDB _id by convention
    private String id;
    private List<String> topics = new ArrayList<>();
    private boolean harmful;
    private String harmfulCategory = "";
    private String harmfulReason = "";
    private long createdAt;
}
