package com.akto.dto.insights;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Shared TTL cache for the two small classifiers behind Atlas Discovery insights
 * (AgentDomainClassifier, ToolCapabilityClassifier). _id is md5(classifierName |
 * input-specific key). value is the classifier's raw JSON output string.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InsightClassificationCache {

    public static final String CLASSIFIER = "classifier";
    public static final String VALUE_JSON = "valueJson";
    public static final String CREATED_AT = "createdAt";
    public static final String EXPIRES_AT = "expiresAt";

    private String id;
    private String classifier;
    private String valueJson;
    private long createdAt;
    private Date expiresAt;
}
