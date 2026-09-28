package com.akto.dto.insights;

import java.util.Date;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cached AI-rendered markdown for one Atlas Discovery/Argus posture insight. _id is a content
 * fingerprint (see InsightFingerprint) over the exact bytes sent to the LLM, so a
 * changed metric or a bumped providerVersion/promptVersion produces a different key
 * rather than serving stale prose over fresh numbers. expiresAt is only a
 * garbage-collection backstop.
 */
@Getter
@Setter
@NoArgsConstructor
public class InsightNarrativeCache {

    public static final String INSIGHT_ID = "insightId";
    public static final String PROVIDER_VERSION = "providerVersion";
    public static final String PROMPT_VERSION = "promptVersion";
    public static final String NARRATIVE_MARKDOWN = "narrativeMarkdown";
    public static final String NARRATIVE_CONCERN = "narrativeConcern";
    public static final String NARRATIVE_IMPACT = "narrativeImpact";
    public static final String NARRATIVE_REMEDIATION = "narrativeRemediation";
    public static final String NARRATIVE_FINDINGS = "narrativeFindings";
    public static final String GENERATED_AT = "generatedAt";
    public static final String EXPIRES_AT = "expiresAt";

    private String id;
    private String insightId;
    private int providerVersion;
    private String narrativeMarkdown;
    private String narrativeConcern;    // nullable — empty when the model had nothing grounded to add over the provider's own draft
    private String narrativeImpact;     // nullable
    private String narrativeRemediation; // nullable
    private long generatedAt;
    private Date expiresAt;
    // ARGUS_POSTURE only — JSON array of {id, title, whyItMatters, remediation}, the model's
    // per-finding rewrites (AgenticInsightNarrativeHandler's own output shape). Null for every
    // ATLAS_DISCOVERY/GUARDRAIL_VIOLATIONS cache entry (those use narrativeConcern/Impact/
    // Remediation above instead). Additive field — an old cached doc reads this back as null,
    // never a deserialization error.
    private String narrativeFindings;

    /** Matches the pre-existing field order exactly (narrativeFindings is set separately, via its
     *  own setter, only by AgenticNarrativeStrategy) — kept as its own constructor rather than a
     *  Lombok @AllArgsConstructor so PostureDrillNarrativeService's own call site (which has no
     *  findings to cache) never needed to change. */
    public InsightNarrativeCache(String id, String insightId, int providerVersion, String narrativeMarkdown,
                                  String narrativeConcern, String narrativeImpact, String narrativeRemediation,
                                  long generatedAt, Date expiresAt) {
        this.id = id;
        this.insightId = insightId;
        this.providerVersion = providerVersion;
        this.narrativeMarkdown = narrativeMarkdown;
        this.narrativeConcern = narrativeConcern;
        this.narrativeImpact = narrativeImpact;
        this.narrativeRemediation = narrativeRemediation;
        this.generatedAt = generatedAt;
        this.expiresAt = expiresAt;
    }
}
