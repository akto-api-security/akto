package com.akto.dto.agentic_sessions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserAnalysisData {

    public static final String SERVICE_ID = "serviceId";
    public static final String UNIQUE_USER_ID = "uniqueUserId";
    public static final String COMPUTED_AT = "computedAt";

    private String serviceId;
    private String uniqueUserId;
    private long windowStart;
    private long windowEnd;
    private List<String> dominantTopics;
    private Map<String, Integer> topicFrequency;
    private int totalQueries;
    private long totalInputTokens;
    private long totalOutputTokens;
    private String aiSummary;
    private long computedAt;
}
