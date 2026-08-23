package com.akto.dto.insights;

import org.bson.codecs.pojo.annotations.BsonId;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * Cached LLM narrative for one insight. One document per distinct (insightId, providerVersion,
 * promptVersion, narrativeInput) combination — see InsightNarrativeCacheDao for the cache-key
 * construction. TTL-deleted via the expiresAt Date field.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InsightNarrativeCache {

    public static final String ID = "_id";
    // md5(accountId | contextSource | insightId | providerVersion | promptVersion | canonicalJson(narrativeInput))
    @BsonId
    private String id;

    public static final String INSIGHT_ID = "insightId";
    private String insightId;

    // Bumped whenever a provider's metric shape changes; baked into the cache key so a shape
    // change can never serve stale prose over fresh numbers.
    public static final String PROVIDER_VERSION = "providerVersion";
    private int providerVersion;

    // Bumped whenever InsightNarrativeHandler's prompt changes, for the same reason.
    public static final String PROMPT_VERSION = "promptVersion";
    private int promptVersion;

    public static final String NARRATIVE_MARKDOWN = "narrativeMarkdown";
    private String narrativeMarkdown;

    public static final String FACTS_USED = "factsUsed";
    private List<String> factsUsed;

    public static final String GENERATED_AT = "generatedAt";
    private int generatedAt;

    public static final String EXPIRES_AT = "expiresAt";
    private Date expiresAt;
}
