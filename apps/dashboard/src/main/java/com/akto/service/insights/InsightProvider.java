package com.akto.service.insights;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;

/**
 * Strategy interface — one implementation per InsightId, registered in InsightProviderRegistry.
 * {@code threatClient} is the calling InsightsAction, for a provider that needs its own bounded
 * event paging beyond what's in the shared bundle (only ever under InsightScope.DETAIL — under
 * LIST a provider must stay in-memory over {@code bundle} and skip network calls entirely).
 */
public interface InsightProvider {

    InsightId getInsightId();

    InsightResult compute(InsightDataBundle bundle, InsightContext ctx, InsightScope scope,
                           AbstractThreatDetectionAction threatClient);
}
