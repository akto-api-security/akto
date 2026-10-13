package com.akto.service.collections;

import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.ApiType;
import com.akto.dto.ApiInfo.AuthType;
import com.akto.dto.ApiStats;

import java.util.HashMap;
import java.util.Map;

/**
 * The summed posture metrics (see {@link PostureMetric}) of some set of apis, plus the few sums that
 * are not metrics of a single api_info document. Turns into the ApiStats the home dashboard charts read.
 */
final class PostureCounts {

    /** apis of collections that are in testing scope, and how many of them were tested recently */
    static final String IN_SCOPE_TOTAL = "inScopeTotal";
    static final String IN_SCOPE_TESTED = "inScopeTested";
    /** the sum of the collections' endpoint counts (single_type_info side), for the missing apis figure */
    static final String ENDPOINTS_COUNT = "endpointsCount";
    /** collections summed, and how many of them have been counted at all */
    static final String ROWS = "rows";
    static final String COUNTED_ROWS = "countedRows";

    private final Map<String, Long> sums;

    PostureCounts(Map<String, Long> sums) {
        this.sums = sums;
    }

    static PostureCounts empty() {
        return new PostureCounts(new HashMap<>());
    }

    /** The sums of a $group row; a field the row lacks is 0. */
    static PostureCounts of(Map<String, Object> row) {
        Map<String, Long> sums = new HashMap<>();
        if (row != null) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getValue() instanceof Number) sums.put(entry.getKey(), ((Number) entry.getValue()).longValue());
            }
        }
        return new PostureCounts(sums);
    }

    /** An action item count the source cannot work out. */
    static final long UNAVAILABLE = -1;

    PostureCounts with(String field, long value) {
        Map<String, Long> copy = new HashMap<>(sums);
        copy.put(field, value);
        return new PostureCounts(copy);
    }

    long get(String field) {
        return sums.getOrDefault(field, 0L);
    }

    /** What is left of these counts once the other's apis are taken out (never below 0). */
    PostureCounts minus(PostureCounts other) {
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, Long> entry : sums.entrySet()) {
            result.put(entry.getKey(), Math.max(entry.getValue() - Math.max(other.get(entry.getKey()), 0), 0));
        }
        return new PostureCounts(result);
    }

    ApiStats toApiStats(int timestamp) {
        ApiStats stats = new ApiStats(timestamp);
        for (int bucket = 0; bucket <= PostureMetric.MAX_RISK_BUCKET; bucket++) {
            putIfAny(stats.getRiskScoreMap(), bucket, get(PostureMetric.riskField(bucket)));
        }
        for (ApiType type : ApiType.values()) {
            putIfAny(stats.getApiTypeMap(), type, get(PostureMetric.API_TYPE_PREFIX + type.name()));
        }
        for (ApiAccessType type : ApiAccessType.values()) {
            putIfAny(stats.getAccessTypeMap(), type, get(PostureMetric.ACCESS_PREFIX + type.name()));
        }
        for (String authType : new String[]{AuthType.UNAUTHENTICATED, AuthType.BASIC, AuthType.AUTHORIZATION_HEADER,
                AuthType.JWT, AuthType.API_TOKEN, AuthType.BEARER, AuthType.API_KEY, AuthType.MTLS, AuthType.SESSION_TOKEN}) {
            putIfAny(stats.getAuthTypeMap(), authType, get(PostureMetric.AUTH_PREFIX + authType));
        }
        putIfAny(stats.getAuthTypeMap(), AuthType.CUSTOM, get(PostureMetric.CUSTOM_AUTH));
        for (String severity : new String[]{"CRITICAL", "HIGH", "MEDIUM", "LOW"}) {
            putIfAny(stats.getCriticalMap(), severity, get(PostureMetric.SEVERITY_PREFIX + severity));
        }
        stats.setTotalAPIs((int) get(PostureMetric.TOTAL));
        stats.setTotalRiskScore((float) get(PostureMetric.RISK_SCORE_SUM) / PostureMetric.RISK_SCORE_SCALE);
        stats.setTotalInScopeForTestingApis((int) get(IN_SCOPE_TOTAL));
        stats.setApisTestedInLookBackPeriod((int) get(IN_SCOPE_TESTED));
        return stats;
    }

    private static <K> void putIfAny(Map<K, Integer> map, K key, long count) {
        if (count > 0) map.put(key, (int) count);
    }
}
