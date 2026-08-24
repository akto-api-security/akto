package com.akto.agent_risk;

public interface RiskCategory {
    String id();

    /** Writes only this category's field on score, clamped 0-100. */
    void apply(RiskContext ctx, AgentRiskScore score);

    /** True if this category's signal is now worse than what other recorded. */
    default boolean stale(RiskContext ctx, AgentRiskScore other) {
        return false;
    }
}
