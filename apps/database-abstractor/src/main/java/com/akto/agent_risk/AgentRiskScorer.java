package com.akto.agent_risk;

import java.util.List;

import com.akto.kafka.AgentRiskKafkaProducer;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.elasticsearch.ElasticSearchClient;
import com.akto.utils.elasticsearch.ElasticSearchClient.KnnHit;


public class AgentRiskScorer {

    private static final LoggerMaker logger = new LoggerMaker(AgentRiskScorer.class, LogDb.DB_ABS);
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
            logger.info("agent-risk cache hit hash=" + hash + " composite=" + cached.getComposite()
                    + " traceId=" + ctx.getTraceId());
            return copyForTrace(cached, ctx, hash, AgentRiskScore.Source.REUSED, cached.getHash(), false);
        }

        String prompt = ctx.getNormalizedPrompt() == null ? "" : ctx.getNormalizedPrompt();
        List<Double> embedding = null;
        if (prompt.length() <= AgentRiskKafkaProducer.getFuzzyMaxChars() && embedClient.isConfigured()) {
            embedding = embedClient.embed(prompt);
            KnnHit hit = ElasticSearchClient.instance().knnSearchAgentRiskScores(
                    embedding, ctx.getAccountId(), ctx.getAgentKey());
            boolean reuse = reusableNeighbor(ctx, hit);
            logger.info("agent-risk knn hash=" + hash
                    + " embedDim=" + embedDim(embedding)
                    + " neighbor=" + neighborSummary(hit)
                    + " reusable=" + reuse
                    + " traceId=" + ctx.getTraceId());
            if (reuse) {
                AgentRiskScore reused = copyForTrace(hit.neighbor, ctx, hash, AgentRiskScore.Source.REUSED,
                        hit.neighbor.getHash(), true);
                reused.setEmbedding(embedding);
                reused.setKnnDistance(hit.distance);
                cache.put(ctx.getAccountId(), hash, reused);
                return reused;
            }
        } else {
            logger.info("agent-risk skip-embed hash=" + hash
                    + " promptChars=" + prompt.length()
                    + " embedConfigured=" + embedClient.isConfigured()
                    + " traceId=" + ctx.getTraceId());
        }

        AgentRiskScore scored = applyRules(ctx, hash);
        scored.setEmbedding(embedding);
        cache.put(ctx.getAccountId(), hash, scored);
        logger.info("agent-risk rules hash=" + hash
                + " composite=" + scored.getComposite()
                + " dataRisk=" + scored.getDataRisk()
                + " toolRisk=" + scored.getToolRisk()
                + " guardrailRisk=" + scored.getGuardrailRisk()
                + " embedDim=" + embedDim(embedding)
                + " traceId=" + ctx.getTraceId());
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

    private static int embedDim(List<Double> embedding) {
        return embedding == null ? 0 : embedding.size();
    }

    private static String neighborSummary(KnnHit hit) {
        if (hit == null || hit.neighbor == null) {
            return "none";
        }
        return "hash=" + hit.neighbor.getHash()
                + " composite=" + hit.neighbor.getComposite()
                + " distance=" + hit.distance
                + " dataClassMax=" + hit.neighbor.getDataClassMax();
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
        out.setGuardrailRisk(Math.max(src.getGuardrailRisk(), GuardrailRisk.detect(ctx)));
        out.setDataClassMax(Math.max(src.getDataClassMax(), DataRisk.detect(ctx)));
        out.setDataOperation(Math.max(src.getDataOperation(), DataRisk.operation(ctx)));
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
        out.recomputeComposite();
        return out;
    }
}
