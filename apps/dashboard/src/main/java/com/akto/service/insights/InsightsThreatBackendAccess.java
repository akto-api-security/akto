package com.akto.service.insights;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.action.threat_detection.SkillSeverityCount;
import com.akto.action.threat_detection.ThreatCategoryCount;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gives the insights loader (a plain service class, not a Struts action) access to
 * AbstractThreatDetectionAction's threat-backend calls, instead of a second,
 * hand-duplicated HTTP client. AbstractThreatDetectionAction has no Struts-specific
 * construction requirements (its constructor just reads an env var), so subclassing it
 * purely to reuse its protected methods is safe outside the request lifecycle — the
 * same pattern AgenticObserveAction already uses when it instantiates ApiCollectionsAction
 * directly for its business logic. The three methods below just re-expose protected
 * members as public so a non-subclass caller (InsightDataLoader) can call them.
 */
class InsightsThreatBackendAccess extends AbstractThreatDetectionAction {

    List<HostSeverityCount> hostSeverityCounts(int startTs, int endTs) {
        return fetchHostSeverityCounts(startTs, endTs);
    }

    List<ThreatCategoryCount> subcategoryWiseCounts(int startTs, int endTs) {
        return fetchSubcategoryWiseCounts(startTs, endTs, null, null);
    }

    List<SkillSeverityCount> skillSeverityCounts(int startTs, int endTs) {
        return fetchSkillSeverityCounts(startTs, endTs);
    }

    /** DETAIL scope only — see InsightDataBundle.fetchPiiEvents. */
    List<DashboardMaliciousEvent> piiEvents(int startTs, int endTs, List<String> subCategories, int limit) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("subCategory", subCategories);
        return violationEvents(startTs, endTs, limit, filters, null);
    }

    /**
     * General violation-event access, for a provider whose filters don't fit piiEvents' hardcoded
     * subCategory shape (an unfiltered sweep, a latestAttack/policy-name filter, or the
     * skill-eval-mode header) — see InsightDataBundle.fetchViolationEvents.
     */
    List<DashboardMaliciousEvent> violationEvents(int startTs, int endTs, int limit, Map<String, Object> filters, String skillEvalMode) {
        return fetchAllMaliciousEvents(startTs, endTs, limit, filters, skillEvalMode);
    }
}
