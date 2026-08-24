package com.akto.agent_risk;

import java.util.Arrays;
import java.util.List;

public final class RiskCategories {

    public static final List<RiskCategory> ALL = Arrays.asList(
            new DataRisk(),
            new ToolRisk()
    );

    private RiskCategories() {}

    public static void applyAll(RiskContext ctx, AgentRiskScore score) {
        ALL.parallelStream().forEach(r -> r.apply(ctx, score));
        score.setComposite(RiskMath.composite(score));
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
