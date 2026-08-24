package com.akto.agent_risk;

public final class RiskMath {

    private RiskMath() {}

    public static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    /** Worst category wins. Add new fields here when categories are added. */
    public static int composite(AgentRiskScore s) {
        if (s == null) {
            return 0;
        }
        return Math.max(s.getDataRisk(), s.getToolRisk());
    }
}
