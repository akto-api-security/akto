package com.akto.dto;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Freshness + lock state of one refreshable metric of api_collection_stats. _id is the metric name.
 * Also carries the account wide summary numbers the SUMMARY metric produces.
 */
@Getter
@Setter
@NoArgsConstructor
public class ApiCollectionStatsMeta {

    public static final String ID = "_id";
    private String id;

    public static final String REFRESHED_AT = "refreshedAt";
    private int refreshedAt;

    // 0 when no refresh is running. a crashed refresher is taken over after the lock timeout
    public static final String REFRESH_STARTED_AT = "refreshStartedAt";
    private int refreshStartedAt;


    public static final String TOTAL_ALLOWED_FOR_TESTING = "totalAllowedForTesting";
    private int totalAllowedForTesting;

    public static final String TOTAL_TESTED_ENDPOINTS = "totalTestedEndpoints";
    private int totalTestedEndpoints;

    public static final String TOTAL_CRITICAL_ENDPOINTS = "totalCriticalEndpoints";
    private int totalCriticalEndpoints;

    public static final String TOTAL_SENSITIVE_ENDPOINTS = "totalSensitiveEndpoints";
    private int totalSensitiveEndpoints;

    // tag key -> its values, for the tag filters; computed once per refresh instead of per page
    public static final String TAG_CHOICES = "tagChoices";
    private Map<String, List<String>> tagChoices;
}
