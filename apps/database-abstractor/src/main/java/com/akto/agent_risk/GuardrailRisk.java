package com.akto.agent_risk;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.akto.dao.context.Context;
import com.akto.data_actor.DbLayer;
import com.akto.dto.GuardrailPolicies;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

/**
 * Coverage: no active policies = 80, any active = 10, this trace violated = 90.
 * Uses DbLayer.fetchGuardrailPolicies (same Mongo path as the HTTP action).
 */
public class GuardrailRisk implements RiskCategory {

    private static final LoggerMaker logger = new LoggerMaker(GuardrailRisk.class, LogDb.DB_ABS);
    private static final long TTL_MS = 5L * 60 * 1000;
    private static final int SCORE_UNPROTECTED = 80;
    private static final int SCORE_PROTECTED = 10;
    private static final int SCORE_VIOLATED = 90;

    private static final class CacheEntry {
        final boolean hasActive;
        final long expiresAt;

        CacheEntry(boolean hasActive, long expiresAt) {
            this.hasActive = hasActive;
            this.expiresAt = expiresAt;
        }
    }

    private static final Map<Integer, CacheEntry> CACHE = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "guardrail";
    }

    @Override
    public void apply(RiskContext ctx, AgentRiskScore score) {
        int risk = detect(ctx);
        score.setGuardrailRisk(RiskMath.clamp(risk));
        logger.info("agent-risk GuardrailRisk applied guardrailRisk=" + score.getGuardrailRisk()
                + " violated=" + (ctx == null ? null : ctx.getGuardrailViolated())
                + " traceId=" + (ctx == null ? "" : ctx.getTraceId()));
    }

    @Override
    public boolean stale(RiskContext ctx, AgentRiskScore other) {
        return other != null && detect(ctx) > other.getGuardrailRisk();
    }

    static int detect(RiskContext ctx) {
        if (ctx != null && Boolean.TRUE.equals(ctx.getGuardrailViolated())) {
            return SCORE_VIOLATED;
        }
        int accountId = ctx == null ? 0 : ctx.getAccountId();
        return hasActivePolicies(accountId) ? SCORE_PROTECTED : SCORE_UNPROTECTED;
    }

    static boolean hasActivePolicies(int accountId) {
        if (accountId <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        CacheEntry cached = CACHE.get(accountId);
        if (cached != null && now < cached.expiresAt) {
            return cached.hasActive;
        }
        boolean hasActive = fetchActive(accountId);
        CACHE.put(accountId, new CacheEntry(hasActive, now + TTL_MS));
        return hasActive;
    }

    private static boolean fetchActive(int accountId) {
        Integer previous = Context.accountId.get();
        try {
            Context.accountId.set(accountId);
            List<GuardrailPolicies> policies = DbLayer.fetchGuardrailPolicies(null, null);
            logger.info("agent-risk GuardrailRisk fetch accountId=" + accountId
                    + " db=" + Context.accountId.get()
                    + " count=" + (policies == null ? 0 : policies.size()));
            if (policies == null || policies.isEmpty()) {
                return false;
            }
            for (GuardrailPolicies p : policies) {
                if (p != null && p.isActive()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            logger.info("agent-risk GuardrailRisk policy fetch failed accountId=" + accountId
                    + " err=" + e.getMessage());
            return false;
        } finally {
            if (previous == null) {
                Context.accountId.remove();
            } else {
                Context.accountId.set(previous);
            }
        }
    }
}
