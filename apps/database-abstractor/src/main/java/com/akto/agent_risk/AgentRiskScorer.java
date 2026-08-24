package com.akto.agent_risk;

import java.util.List;

import com.akto.kafka.AgentRiskKafkaProducer;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.elasticsearch.ElasticSearchClient;
import com.akto.utils.elasticsearch.ElasticSearchClient.KnnHit;


public class AgentRiskScorer {

    private static final AgentRiskScorer INSTANCE = new AgentRiskScorer();

    public static AgentRiskScorer instance() {
        return INSTANCE;
    }

    private final RiskScoreCache cache = RiskScoreCache.instance();
    private final EmbedKnnClient embedClient = EmbedKnnClient.instance();

    public AgentRiskScore score(AgentQueryRecord record, int fallbackAccountId) {
        if (record == null) {
            return null;
        }
        if (record.getAccountId() == 0 && fallbackAccountId > 0) {
            record.setAccountId(fallbackAccountId);
        }
        RiskContext ctx = RiskContext.from(record);
        String hash = ctx.hash();

        AgentRiskScore cached = cache.get(ctx.getAccountId(), hash);
        if (canReuse(ctx, cached)) {
            return copyForTrace(cached, ctx, hash, AgentRiskScore.Source.REUSED, cached.getHash(), false);
        }

        String prompt = ctx.getNormalizedPrompt() == null ? "" : ctx.getNormalizedPrompt();
        List<Double> embedding = null;
        if (prompt.length() <= AgentRiskKafkaProducer.getFuzzyMaxChars() && embedClient.isConfigured()) {
            embedding = embedClient.embed(prompt);
            KnnHit hit = ElasticSearchClient.instance().knnSearchAgentRiskScores(
                    embedding, ctx.getAccountId(), ctx.getAgentKey());
            if (reusableNeighbor(ctx, hit)) {
                AgentRiskScore reused = copyForTrace(hit.neighbor, ctx, hash, AgentRiskScore.Source.REUSED,
                        hit.neighbor.getHash(), true);
                reused.setEmbedding(embedding);
                reused.setKnnDistance(hit.distance);
                cache.put(ctx.getAccountId(), hash, reused);
                return reused;
            }
        }

        AgentRiskScore scored = applyRules(ctx, hash);
        scored.setEmbedding(embedding);
        cache.put(ctx.getAccountId(), hash, scored);
        return scored;
    }

    static AgentRiskScore applyRules(RiskContext ctx, String hash) {
        AgentRiskScore out = new AgentRiskScore();
        out.setHash(hash);
        out.setAccountId(ctx.getAccountId());
        out.setAgentKey(ctx.getAgentKey());
        out.setToolFingerprint(ctx.getToolFingerprint());
        out.setPrivilegeClass(ctx.getPrivilegeClass());
        out.setTraceId(ctx.getTraceId());
        out.setSpanId(ctx.getSpanId());
        out.setTimestamp(System.currentTimeMillis());
        out.setSource(AgentRiskScore.Source.RULES);
        out.setApiCollectionId(ctx.getApiCollectionId());
        RiskCategories.applyAll(ctx, out);
        return out;
    }

    static boolean canReuse(RiskContext ctx, AgentRiskScore other) {
        if (ctx == null || other == null) {
            return false;
        }
        if (ctx.getAccountId() != other.getAccountId()) {
            return false;
        }
        if (!eq(ctx.getAgentKey(), other.getAgentKey())) {
            return false;
        }
        if (!eq(ctx.getPrivilegeClass(), other.getPrivilegeClass())) {
            return false;
        }
        return !RiskCategories.anyStale(ctx, other);
    }

    static boolean reusableNeighbor(RiskContext ctx, KnnHit hit) {
        if (hit == null || hit.neighbor == null) {
            return false;
        }
        if (hit.distance > AgentRiskKafkaProducer.getKnnDistanceThreshold()) {
            return false;
        }
        if (hit.neighbor.getComposite() >= AgentRiskKafkaProducer.getHighRiskComposite()) {
            return false;
        }
        return canReuse(ctx, hit.neighbor);
    }

    private static boolean eq(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    private static AgentRiskScore copyForTrace(AgentRiskScore src, RiskContext ctx, String hash,
                                              AgentRiskScore.Source source, String neighborId,
                                              boolean hardMatched) {
        AgentRiskScore out = new AgentRiskScore();
        out.setComposite(src.getComposite());
        out.setDataRisk(src.getDataRisk());
        out.setToolRisk(src.getToolRisk());
        out.setDataClassMax(Math.max(src.getDataClassMax(), DataRisk.detect(ctx)));
        out.setSource(source);
        out.setHash(hash);
        out.setNeighborId(neighborId);
        out.setAccountId(ctx.getAccountId());
        out.setAgentKey(ctx.getAgentKey());
        out.setToolFingerprint(ctx.getToolFingerprint());
        out.setPrivilegeClass(ctx.getPrivilegeClass());
        out.setTraceId(ctx.getTraceId());
        out.setSpanId(ctx.getSpanId());
        out.setTimestamp(System.currentTimeMillis());
        out.setHardConstraintsMatched(hardMatched);
        out.setEmbedding(src.getEmbedding());
        out.setApiCollectionId(ctx.getApiCollectionId());
        out.setKnnDistance(src.getKnnDistance());
        return out;
    }
}
