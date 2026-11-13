package com.akto.utils;

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
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.RiskScoreFactor;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

/**
 * Reads {@link ApiInfo#getRiskScoreFactors()} written by the risk-score cron.
 * Does not rebuild or invent lines at read time.
 */
public final class RiskScoreExplainer {

    private RiskScoreExplainer() {}

    public static final class Explanation {
        public final List<BasicDBObject> factors;

        public Explanation(List<BasicDBObject> factors) {
            this.factors = factors != null ? factors : Collections.emptyList();
        }
    }

    /** Returns the list saved with the score, or an empty list when missing/empty. */
    public static Explanation explain(ApiInfo apiInfo) {
        if (apiInfo == null || apiInfo.getRiskScoreFactors() == null || apiInfo.getRiskScoreFactors().isEmpty()) {
            return new Explanation(Collections.emptyList());
        }
        return new Explanation(toBasicDBList(apiInfo.getRiskScoreFactors()));
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
                Accumulators.first(ApiInfo.RISK_SCORE, "$" + ApiInfo.RISK_SCORE),
                Accumulators.first(ApiInfo.RISK_SCORE_FACTORS, "$" + ApiInfo.RISK_SCORE_FACTORS)
        ));

        try (MongoCursor<BasicDBObject> cursor =
                     ApiInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor()) {
            while (cursor.hasNext()) {
                BasicDBObject doc = cursor.next();
                Object idObj = doc.get("_id");
                if (!(idObj instanceof Number)) continue;
                int collectionId = ((Number) idObj).intValue();
                ApiInfo info = new ApiInfo();
                info.setRiskScore(asFloat(doc.get(ApiInfo.RISK_SCORE)));
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

    private static float asFloat(Object v) {
        if (v instanceof Number) return ((Number) v).floatValue();
        return 0f;
    }

    /** Package-private for tests — cron-written docs may include a discriminator `_t` field. */
    @SuppressWarnings("unchecked")
    static List<RiskScoreFactor> parseFactors(Object raw) {
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
