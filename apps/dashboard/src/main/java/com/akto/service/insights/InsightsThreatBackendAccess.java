package com.akto.service.insights;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.action.threat_detection.ThreatCategoryCount;

import java.util.Arrays;
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

    /** DETAIL scope only — see InsightDataBundle.fetchPiiEvents. */
    List<DashboardMaliciousEvent> piiEvents(int startTs, int endTs, List<String> subCategories, int limit) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("subCategory", subCategories);
        return fetchAllMaliciousEvents(startTs, endTs, limit, filters);
    }
}
