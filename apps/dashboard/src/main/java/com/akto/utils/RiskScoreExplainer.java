package com.akto.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.RiskScoreFactor;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AgenticObserveUtil;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

/**
 * Reads {@link ApiInfo#getRiskScoreFactors()} written by the risk-score cron.
 * Does not rebuild or invent lines at read time.
 */
public final class RiskScoreExplainer {

    private static final LoggerMaker loggerMaker = new LoggerMaker(RiskScoreExplainer.class, LogDb.DASHBOARD);
    private static final long MAX_TIME_SECONDS = 5L;
    private static final String READ_FACTORS_ENV = "AKTO_READ_RISK_SCORE_FACTORS";

    private RiskScoreExplainer() {}

    public static final class Explanation {
        public final List<BasicDBObject> factors;

        public Explanation(List<BasicDBObject> factors) {
            this.factors = factors != null ? factors : Collections.emptyList();
        }
    }

    /** Result of loading highest-risk api_info per collection; {@code unavailable} when the query failed. */
    public static final class LoadResult {
        public final Map<Integer, ApiInfo> byCollection;
        public final boolean unavailable;

        public LoadResult(Map<Integer, ApiInfo> byCollection, boolean unavailable) {
            this.byCollection = byCollection != null ? byCollection : Collections.emptyMap();
            this.unavailable = unavailable;
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
     * Prefer {@link #loadHighestRiskApiInfoByCollectionResult} when callers need the unavailable flag.
     */
    public static Map<Integer, ApiInfo> loadHighestRiskApiInfoByCollection(Collection<Integer> collectionIds) {
        return loadHighestRiskApiInfoByCollectionResult(collectionIds).byCollection;
    }

    public static LoadResult loadHighestRiskApiInfoByCollectionResult(Collection<Integer> collectionIds) {
        Map<Integer, ApiInfo> out = new HashMap<>();
        if (collectionIds == null || collectionIds.isEmpty()) {
            return new LoadResult(out, false);
        }
        if (!isReadFactorsEnabled()) {
            loggerMaker.infoAndAddToDb(
                    "Skipping riskScoreFactors load (AKTO_READ_RISK_SCORE_FACTORS=false) accountId="
                            + Context.accountId.get(),
                    LogDb.DASHBOARD);
            return new LoadResult(out, false);
        }
        Set<Integer> ids = new HashSet<>();
        for (Integer id : collectionIds) {
            if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) {
            return new LoadResult(out, false);
        }

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.in(ApiInfo.ID_API_COLLECTION_ID, ids)));
        pipeline.add(Aggregates.project(Projections.include(
                ApiInfo.ID_API_COLLECTION_ID,
                ApiInfo.RISK_SCORE,
                ApiInfo.RISK_SCORE_FACTORS,
                ApiInfo.RISK_SCORE_FACTORS_SCORE)));
        // Match {_id.apiCollectionId, riskScore} index; _id tiebreaks equal scores.
        pipeline.add(Aggregates.sort(Sorts.orderBy(
                Sorts.ascending(ApiInfo.ID_API_COLLECTION_ID),
                Sorts.descending(ApiInfo.RISK_SCORE),
                Sorts.ascending("_id"))));
        pipeline.add(Aggregates.group(
                "$" + ApiInfo.ID_API_COLLECTION_ID,
                Accumulators.first(ApiInfo.RISK_SCORE, "$" + ApiInfo.RISK_SCORE),
                Accumulators.first(ApiInfo.RISK_SCORE_FACTORS, "$" + ApiInfo.RISK_SCORE_FACTORS),
                Accumulators.first(ApiInfo.RISK_SCORE_FACTORS_SCORE, "$" + ApiInfo.RISK_SCORE_FACTORS_SCORE)
        ));

        long started = System.currentTimeMillis();
        try (MongoCursor<BasicDBObject> cursor =
                     ApiInfoDao.instance.aggregateWithRbac(pipeline)
                             .allowDiskUse(true)
                             .maxTime(MAX_TIME_SECONDS, TimeUnit.SECONDS)
                             .cursor()) {
            while (cursor.hasNext()) {
                BasicDBObject doc = cursor.next();
                Object idObj = doc.get("_id");
                if (!(idObj instanceof Number)) continue;
                int collectionId = ((Number) idObj).intValue();
                ApiInfo info = new ApiInfo();
                info.setRiskScore(asFloat(doc.get(ApiInfo.RISK_SCORE)));
                info.setRiskScoreFactors(parseFactors(doc.get(ApiInfo.RISK_SCORE_FACTORS)));
                if (doc.containsField(ApiInfo.RISK_SCORE_FACTORS_SCORE)
                        && doc.get(ApiInfo.RISK_SCORE_FACTORS_SCORE) != null) {
                    info.setRiskScoreFactorsScore(asFloat(doc.get(ApiInfo.RISK_SCORE_FACTORS_SCORE)));
                    info.setRiskScoreFactorsScorePresent(true);
                }
                out.put(collectionId, info);
            }
            return new LoadResult(out, false);
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - started;
            loggerMaker.errorAndAddToDb(e,
                    "riskScoreFactors load failed accountId=" + Context.accountId.get()
                            + " collections=" + ids.size() + " durationMs=" + durationMs);
            return new LoadResult(Collections.emptyMap(), true);
        }
    }

    public static void putExplanation(BasicDBObject row, Explanation explanation) {
        if (row == null || explanation == null) return;
        if (!explanation.factors.isEmpty()) row.put("riskScoreFactors", explanation.factors);
    }

    /**
     * Attach factors only when the displayed score round-equals the winning api_info score,
     * and when a stored {@code riskScoreFactorsScore} is present it must also match that api score.
     */
    public static void putExplanationIfMatches(BasicDBObject row, ApiInfo winning, double displayedScore) {
        if (row == null || !scoresMatch(winning, displayedScore)) return;
        putExplanation(row, explain(winning));
    }

    public static void putExplanationIfMatches(Map<String, Object> row, ApiInfo winning, double displayedScore) {
        if (row == null || !scoresMatch(winning, displayedScore)) return;
        Explanation explanation = explain(winning);
        if (!explanation.factors.isEmpty()) {
            row.put("riskScoreFactors", explanation.factors);
        }
    }

    /** True when factors may be shown next to {@code displayedScore}. */
    public static boolean scoresMatch(ApiInfo winning, double displayedScore) {
        if (winning == null) return false;
        if (AgenticObserveUtil.roundRiskScore(winning.getRiskScore())
                != AgenticObserveUtil.roundRiskScore(displayedScore)) {
            return false;
        }
        if (winning.isRiskScoreFactorsScorePresent()
                && AgenticObserveUtil.roundRiskScore(winning.getRiskScoreFactorsScore())
                != AgenticObserveUtil.roundRiskScore(winning.getRiskScore())) {
            return false;
        }
        return true;
    }

    public static List<BasicDBObject> factorsIfMatches(ApiInfo winning, double displayedScore) {
        if (!scoresMatch(winning, displayedScore)) return Collections.emptyList();
        return explain(winning).factors;
    }

    /**
     * True when a collection produced the displayed score. {@code 0} is the unset primitive
     * (no collection won). Hashed collection ids are often negative and must still load.
     */
    public static boolean isSetCollectionId(int collectionId) {
        return collectionId != 0;
    }

    public static boolean isSetCollectionId(Number collectionId) {
        return collectionId != null && collectionId.intValue() != 0;
    }

    static boolean isReadFactorsEnabled() {
        String raw = System.getenv(READ_FACTORS_ENV);
        if (raw == null || raw.isEmpty()) return true;
        return Boolean.parseBoolean(raw);
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
