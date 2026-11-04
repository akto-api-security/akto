package com.akto.utils;

import static com.akto.jobs.utils.Utils.getRiskScoreValueFromSeverityScore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

/**
 * Breaks {@link ApiInfoDao#getRiskScore} into labeled point lines for customer-facing tooltips.
 * Same conditions as the stored formula — do not invent a second scoring model here.
 */
public final class RiskScoreExplainer {

    private static final float RESIDUAL_EPS = 0.05f;

    private RiskScoreExplainer() {}

    /**
     * Factor lines for {@code apiInfo}, residual-adjusted so points sum to {@code displayedScore}
     * (the badge the UI shows — typically the stored / rounded max).
     */
    public static List<BasicDBObject> explain(ApiInfo apiInfo, float displayedScore) {
        List<BasicDBObject> factors = buildFactors(apiInfo);
        float sum = 0f;
        for (BasicDBObject f : factors) {
            sum += ((Number) f.get("points")).floatValue();
        }
        float residual = displayedScore - sum;
        if (Math.abs(residual) >= RESIDUAL_EPS) {
            factors.add(factor(roundPoints(residual), "Other factors"));
        }
        return factors;
    }

    public static List<BasicDBObject> explain(ApiInfo apiInfo) {
        if (apiInfo == null) return Collections.emptyList();
        return explain(apiInfo, apiInfo.getRiskScore());
    }

    /**
     * One highest-{@code riskScore} {@link ApiInfo} per collection id (fields needed for
     * {@link #explain} only). Empty input → empty map; missing collections are omitted.
     */
    public static Map<Integer, ApiInfo> loadHighestRiskApiInfoByCollection(Collection<Integer> collectionIds) {
        Map<Integer, ApiInfo> out = new HashMap<>();
        if (collectionIds == null || collectionIds.isEmpty()) return out;
        Set<Integer> ids = new HashSet<>();
        for (Integer id : collectionIds) {
            if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) return out;

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.in(ApiInfo.ID_API_COLLECTION_ID, ids)));
        pipeline.add(Aggregates.sort(Sorts.descending(ApiInfo.RISK_SCORE)));
        pipeline.add(Aggregates.group(
                "$" + ApiInfo.ID_API_COLLECTION_ID,
                Accumulators.first(ApiInfo.DISCOVERED_TIMESTAMP, "$" + ApiInfo.DISCOVERED_TIMESTAMP),
                Accumulators.first(ApiInfo.API_ACCESS_TYPES, "$" + ApiInfo.API_ACCESS_TYPES),
                Accumulators.first(ApiInfo.IS_SENSITIVE, "$" + ApiInfo.IS_SENSITIVE),
                Accumulators.first(ApiInfo.THREAT_SCORE, "$" + ApiInfo.THREAT_SCORE),
                Accumulators.first(ApiInfo.SEVERITY_SCORE, "$" + ApiInfo.SEVERITY_SCORE),
                Accumulators.first(ApiInfo.RISK_SCORE, "$" + ApiInfo.RISK_SCORE)
        ));

        try (MongoCursor<BasicDBObject> cursor =
                     ApiInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor()) {
            while (cursor.hasNext()) {
                BasicDBObject doc = cursor.next();
                Object idObj = doc.get("_id");
                if (!(idObj instanceof Number)) continue;
                int collectionId = ((Number) idObj).intValue();
                ApiInfo info = new ApiInfo();
                info.setDiscoveredTimestamp(doc.getInt(ApiInfo.DISCOVERED_TIMESTAMP, 0));
                info.setIsSensitive(doc.getBoolean(ApiInfo.IS_SENSITIVE, false));
                info.setThreatScore(asFloat(doc.get(ApiInfo.THREAT_SCORE)));
                info.setSeverityScore(asFloat(doc.get(ApiInfo.SEVERITY_SCORE)));
                info.setRiskScore(asFloat(doc.get(ApiInfo.RISK_SCORE)));
                info.setApiAccessTypes(parseAccessTypes(doc.get(ApiInfo.API_ACCESS_TYPES)));
                out.put(collectionId, info);
            }
        } catch (Exception e) {
            // Caller still returns rows without factors rather than failing the whole page.
        }
        return out;
    }

    /** Mirrors {@link ApiInfoDao#getRiskScore} condition-for-condition. */
    static List<BasicDBObject> buildFactors(ApiInfo apiInfo) {
        List<BasicDBObject> factors = new ArrayList<>();
        if (apiInfo == null) return factors;

        boolean isThreatDetected = apiInfo.getThreatScore() > 0;
        if ((Context.now() - apiInfo.getDiscoveredTimestamp()) <= Constants.ONE_MONTH_TIMESTAMP && !isThreatDetected) {
            factors.add(factor(1f, "Discovered in the last 30 days"));
        }
        Set<ApiAccessType> accessTypes = apiInfo.getApiAccessTypes();
        if (accessTypes != null && accessTypes.contains(ApiAccessType.PUBLIC)) {
            factors.add(factor(1f, "Publicly accessible"));
        }
        if (apiInfo.getIsSensitive()) {
            factors.add(factor(1f, "Sensitive data in responses"));
        }
        if (isThreatDetected) {
            factors.add(factor(1f, "Guardrail or threat activity"));
        }
        float severityPoints = getRiskScoreValueFromSeverityScore(apiInfo.getSeverityScore());
        if (severityPoints > 0) {
            factors.add(factor(severityPoints, severityLabel(severityPoints)));
        }
        return factors;
    }

    private static String severityLabel(float severityPoints) {
        if (severityPoints >= 2f) return "High or critical open test findings";
        if (severityPoints >= 1f) return "Medium open test findings";
        return "Low open test findings";
    }

    private static BasicDBObject factor(float points, String label) {
        BasicDBObject f = new BasicDBObject();
        f.put("points", points);
        f.put("label", label);
        return f;
    }

    private static float roundPoints(float points) {
        return Math.round(points * 10f) / 10f;
    }

    private static float asFloat(Object v) {
        if (v instanceof Number) return ((Number) v).floatValue();
        return 0f;
    }

    @SuppressWarnings("unchecked")
    private static Set<ApiAccessType> parseAccessTypes(Object raw) {
        Set<ApiAccessType> out = new HashSet<>();
        if (!(raw instanceof List)) return out;
        for (Object item : (List<?>) raw) {
            if (item == null) continue;
            try {
                out.add(ApiAccessType.valueOf(String.valueOf(item)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }
}
