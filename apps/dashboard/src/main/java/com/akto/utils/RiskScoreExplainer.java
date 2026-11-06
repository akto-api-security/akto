package com.akto.utils;

import static com.akto.jobs.utils.Utils.getRiskScoreValueFromSeverityScore;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.RiskScoreFactor;
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

    private static final float SHORTFALL_EPS = 0.05f;
    private static final int MAX_DETAIL_NAMES = 5;
    private static final DateTimeFormatter NOTE_DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).withZone(ZoneOffset.UTC);

    private RiskScoreExplainer() {}

    public static final class Explanation {
        public final List<BasicDBObject> factors;
        /** Present only when no saved list exists and live factors sum to less than the badge. */
        public final String note;

        public Explanation(List<BasicDBObject> factors, String note) {
            this.factors = factors != null ? factors : Collections.emptyList();
            this.note = note;
        }
    }

    /**
     * Prefer the list written with the score. When missing, rebuild from live fields and, if that
     * sum falls short of {@code displayedScore}, attach a dated note instead of inventing points.
     */
    public static Explanation explain(ApiInfo apiInfo, float displayedScore) {
        if (apiInfo != null && apiInfo.getRiskScoreFactors() != null && !apiInfo.getRiskScoreFactors().isEmpty()) {
            return new Explanation(toBasicDBList(apiInfo.getRiskScoreFactors()), null);
        }
        List<BasicDBObject> factors = toBasicDBList(buildFactors(
                apiInfo,
                apiInfo != null && apiInfo.getIsSensitive(),
                apiInfo != null ? apiInfo.getSeverityScore() : 0f,
                null,
                null));
        float sum = sumPoints(factors);
        String note = null;
        if (displayedScore - sum >= SHORTFALL_EPS) {
            note = shortfallNote(apiInfo != null ? apiInfo.getLastCalculatedTime() : 0);
        }
        return new Explanation(factors, note);
    }

    public static Explanation explain(ApiInfo apiInfo) {
        if (apiInfo == null) return new Explanation(Collections.emptyList(), null);
        return explain(apiInfo, apiInfo.getRiskScore());
    }

    /**
     * Factor list written in the same update as {@code riskScore}. Uses the same inputs that go
     * into {@link ApiInfoDao#getRiskScore} so the saved lines always match the number.
     */
    public static List<RiskScoreFactor> buildFactors(
            ApiInfo apiInfo,
            boolean isSensitive,
            float severityScore,
            List<String> issueNames,
            List<String> sensitiveSubtypes) {
        List<RiskScoreFactor> factors = new ArrayList<>();
        if (apiInfo == null) return factors;

        boolean isThreatDetected = apiInfo.getThreatScore() > 0;
        if ((Context.now() - apiInfo.getDiscoveredTimestamp()) <= Constants.ONE_MONTH_TIMESTAMP && !isThreatDetected) {
            factors.add(new RiskScoreFactor(1f, "Discovered in the last 30 days", null));
        }
        Set<ApiAccessType> accessTypes = apiInfo.getApiAccessTypes();
        if (accessTypes != null && accessTypes.contains(ApiAccessType.PUBLIC)) {
            factors.add(new RiskScoreFactor(1f, "Publicly accessible", null));
        }
        if (isSensitive) {
            factors.add(new RiskScoreFactor(1f, "Sensitive data in responses", joinDetail(sensitiveSubtypes)));
        }
        if (isThreatDetected) {
            factors.add(new RiskScoreFactor(1f, "Guardrail or threat activity", null));
        }
        float severityPoints = getRiskScoreValueFromSeverityScore(severityScore);
        if (severityPoints > 0) {
            factors.add(new RiskScoreFactor(severityPoints, severityLabel(severityPoints), joinDetail(issueNames)));
        }
        return factors;
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
                Accumulators.first(ApiInfo.RISK_SCORE, "$" + ApiInfo.RISK_SCORE),
                Accumulators.first(ApiInfo.RISK_SCORE_FACTORS, "$" + ApiInfo.RISK_SCORE_FACTORS),
                Accumulators.first(ApiInfo.LAST_CALCULATED_TIME, "$" + ApiInfo.LAST_CALCULATED_TIME)
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
                info.setLastCalculatedTime(doc.getInt(ApiInfo.LAST_CALCULATED_TIME, 0));
                info.setApiAccessTypes(parseAccessTypes(doc.get(ApiInfo.API_ACCESS_TYPES)));
                info.setRiskScoreFactors(parseFactors(doc.get(ApiInfo.RISK_SCORE_FACTORS)));
                out.put(collectionId, info);
            }
        } catch (Exception e) {
            // Caller still returns rows without factors rather than failing the whole page.
        }
        return out;
    }

    public static void putExplanation(BasicDBObject row, Explanation explanation) {
        if (row == null || explanation == null) return;
        if (!explanation.factors.isEmpty()) row.put("riskScoreFactors", explanation.factors);
        if (explanation.note != null) row.put("riskScoreNote", explanation.note);
    }

    static String shortfallNote(int lastCalculatedTime) {
        if (lastCalculatedTime > 0) {
            String when = NOTE_DATE.format(Instant.ofEpochSecond(lastCalculatedTime));
            return "The rest of this score was recorded before explanations were saved (last calculated "
                    + when + ").";
        }
        return "The rest of this score was recorded before explanations were saved.";
    }

    private static String severityLabel(float severityPoints) {
        if (severityPoints >= 2f) return "High or critical open test findings";
        if (severityPoints >= 1f) return "Medium open test findings";
        return "Low open test findings";
    }

    private static String joinDetail(List<String> names) {
        if (names == null || names.isEmpty()) return null;
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String n : names) {
            if (n != null && !n.trim().isEmpty()) unique.add(n.trim());
        }
        if (unique.isEmpty()) return null;
        List<String> list = new ArrayList<>(unique);
        if (list.size() <= MAX_DETAIL_NAMES) return String.join(", ", list);
        int extra = list.size() - MAX_DETAIL_NAMES;
        return String.join(", ", list.subList(0, MAX_DETAIL_NAMES)) + " (+" + extra + " more)";
    }

    private static List<BasicDBObject> toBasicDBList(List<RiskScoreFactor> factors) {
        List<BasicDBObject> out = new ArrayList<>();
        if (factors == null) return out;
        for (RiskScoreFactor f : factors) {
            if (f == null) continue;
            BasicDBObject row = new BasicDBObject();
            row.put("points", f.getPoints());
            row.put("label", f.getLabel());
            if (f.getDetail() != null && !f.getDetail().isEmpty()) row.put("detail", f.getDetail());
            out.add(row);
        }
        return out;
    }

    private static float sumPoints(List<BasicDBObject> factors) {
        float sum = 0f;
        for (BasicDBObject f : factors) {
            Object p = f.get("points");
            if (p instanceof Number) sum += ((Number) p).floatValue();
        }
        return sum;
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

    @SuppressWarnings("unchecked")
    private static List<RiskScoreFactor> parseFactors(Object raw) {
        List<RiskScoreFactor> out = new ArrayList<>();
        if (!(raw instanceof List)) return out;
        for (Object item : (List<?>) raw) {
            if (!(item instanceof Map)) continue;
            Map<?, ?> map = (Map<?, ?>) item;
            Object pointsObj = map.get("points");
            Object labelObj = map.get("label");
            if (!(pointsObj instanceof Number) || labelObj == null) continue;
            Object detailObj = map.get("detail");
            out.add(new RiskScoreFactor(
                    ((Number) pointsObj).floatValue(),
                    String.valueOf(labelObj),
                    detailObj != null ? String.valueOf(detailObj) : null));
        }
        return out;
    }
}
