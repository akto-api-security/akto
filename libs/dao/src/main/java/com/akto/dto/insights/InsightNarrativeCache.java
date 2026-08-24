package com.akto.dto.insights;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cached AI-rendered markdown for one Atlas Discovery insight. _id is a content
 * fingerprint (see InsightFingerprint) over the exact bytes sent to the LLM, so a
 * changed metric or a bumped providerVersion/promptVersion produces a different key
 * rather than serving stale prose over fresh numbers. expiresAt is only a
 * garbage-collection backstop.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InsightNarrativeCache {

    public static final String INSIGHT_ID = "insightId";
    public static final String PROVIDER_VERSION = "providerVersion";
    public static final String PROMPT_VERSION = "promptVersion";
    public static final String NARRATIVE_MARKDOWN = "narrativeMarkdown";
    public static final String GENERATED_AT = "generatedAt";
    public static final String EXPIRES_AT = "expiresAt";

    private String id;
    private String insightId;
    private int providerVersion;
    private int promptVersion;
    private String narrativeMarkdown;
    private long generatedAt;
    private Date expiresAt;
}
