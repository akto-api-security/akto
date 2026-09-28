package com.akto.service.insights.narrative;

import com.akto.dto.insights.InsightNarrativeCache;
import com.akto.service.insights.InsightResult;
import com.mongodb.BasicDBObject;

import java.util.Date;

/**
 * How an InsightResult's narrative is built, generated, applied, and cached — the one axis
 * ATLAS_DISCOVERY/GUARDRAIL_VIOLATIONS insights (concern/impact/remediation) and ARGUS_POSTURE
 * findings (one title/whyItMatters/remediation per Finding) genuinely differ on. InsightService
 * picks the strategy by InsightId.Group and otherwise treats every insight identically — same
 * bundle load, same cache TTL/fingerprinting, same one-LLM-call-on-cache-miss shape.
 */
public interface InsightNarrativeStrategy {

    /** The exact JSON sent to the LLM handler — see each implementation for its own shape. */
    BasicDBObject buildNarrativeInput(InsightResult r);

    /** Folded into the cache fingerprint so the two strategies' entries can never collide even if
     *  their narrativeInput happened to serialize identically for some degenerate input. */
    String promptTag();

    /** Runs the LLM call (with its one retry) and returns either the accepted fields or
     *  {"error": "<reason>"}. */
    BasicDBObject generate(BasicDBObject narrativeInput);

    /** Applies a handler result — freshly generated or rehydrated from cache via fromCache — onto
     *  the InsightResult. Never clears an existing draft field, only overwrites when the handler
     *  actually returned something non-empty for it (see each implementation). */
    void apply(InsightResult r, BasicDBObject output);

    /** Packs a freshly generated result into the shared cache DTO. */
    InsightNarrativeCache toCache(String fingerprint, InsightResult r, int providerVersion,
                                   BasicDBObject output, long generatedAt, Date expiresAt);

    /** Rehydrates a cached doc back into the same {@code output} shape generate()/apply() use. */
    BasicDBObject fromCache(InsightNarrativeCache cached);
}
