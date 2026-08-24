package com.akto.agent_risk;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentRiskScore {

    public enum Source { RULES, REUSED }

    private int composite;
    private int dataRisk;
    private int toolRisk;
    private int dataClassMax;
    private Source source;
    private String hash;
    private String neighborId;
    private int accountId;
    private String agentKey;
    private String toolFingerprint;
    private String privilegeClass;
    private String traceId;
    private String spanId;
    private long timestamp;
    private boolean hardConstraintsMatched;
    private List<Double> embedding;
    private Integer apiCollectionId;
    private double knnDistance;

    public void recomputeComposite() {
        this.composite = RiskMath.composite(this);
    }
}
