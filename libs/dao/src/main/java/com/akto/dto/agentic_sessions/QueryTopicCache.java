package com.akto.dto.agentic_sessions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class QueryTopicCache {

    public static final String DOMAIN           = "domain";
    public static final String SUB_DOMAIN       = "subDomain";
    public static final String HARMFUL          = "harmful";
    public static final String HARMFUL_CATEGORY = "harmfulCategory";
    public static final String HARMFUL_REASON   = "harmfulReason";
    public static final String CREATED_AT       = "createdAt";

    // Field named "id" maps to MongoDB _id by convention
    private String id;
    private String domain     = "";
    private String subDomain  = "";
    private boolean harmful;
    private String harmfulCategory = "";
    private String harmfulReason = "";
    private long createdAt;
}