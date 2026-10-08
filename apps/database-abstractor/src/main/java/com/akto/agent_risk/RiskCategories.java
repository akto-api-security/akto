package com.akto.agent_risk;

import java.util.Arrays;
import java.util.List;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

public final class RiskCategories {

    private static final LoggerMaker logger = new LoggerMaker(RiskCategories.class, LogDb.DB_ABS);

    public static final List<RiskCategory> ALL = Arrays.asList(
            new DataRisk(),
            new ToolRisk(),
            new GuardrailRisk()
    );

    private RiskCategories() {}

    public static void applyAll(RiskContext ctx, AgentRiskScore score) {
        int accountId = ctx == null ? 0 : ctx.getAccountId();
        ALL.parallelStream().forEach(r -> {
            Integer previous = Context.accountId.get();
            try {
                if (accountId > 0) {
                    Context.accountId.set(accountId);
                }
                r.apply(ctx, score);
            } finally {
                if (previous == null) {
                    Context.accountId.remove();
                } else {
                    Context.accountId.set(previous);
                }
            }
        });
        score.setComposite(RiskMath.composite(score));
        logger.info("agent-risk categories"
                + " data=" + score.getDataRisk()
                + " tool=" + score.getToolRisk()
                + " guardrail=" + score.getGuardrailRisk()
                + " sensitivity=" + score.getDataClassMax()
                + " operation=" + score.getDataOperation()
                + " composite=" + score.getComposite()
                + " traceId=" + (ctx == null ? "" : ctx.getTraceId()));
    }

    public static boolean anyStale(RiskContext ctx, AgentRiskScore other) {
        if (other == null) {
            return true;
        }
        for (RiskCategory r : ALL) {
            if (r.stale(ctx, other)) {
                return true;
            }
        }
        return false;
    }
}
