package com.akto.agent_risk;

import java.util.regex.Pattern;

public class DataRisk implements RiskCategory {

    private static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern JWT = Pattern.compile("eyj[a-z0-9_-]+\\.[a-z0-9_-]+\\.[a-z0-9_-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PEM = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern PAN = Pattern.compile("\\b[A-Z]{5}\\d{4}[A-Z]\\b");

    @Override
    public String id() {
        return "data";
    }

    @Override
    public void apply(RiskContext ctx, AgentRiskScore score) {
        int raw = detect(ctx);
        score.setDataClassMax(raw);
        score.setDataRisk(RiskMath.clamp(raw));
    }

    @Override
    public boolean stale(RiskContext ctx, AgentRiskScore other) {
        return other != null && detect(ctx) > other.getDataClassMax();
    }

    static int detect(RiskContext ctx) {
        return ctx == null ? 0 : detect(ctx.getRawText());
    }

    static int detect(String text) {
        if (text == null) {
            return 0;
        }
        int max = 0;
        if (EMAIL.matcher(text).find()) {
            max = Math.max(max, 40);
        }
        if (PAN.matcher(text).find() || SSN.matcher(text).find()) {
            max = Math.max(max, 80);
        }
        if (JWT.matcher(text).find() || PEM.matcher(text).find()) {
            max = Math.max(max, 90);
        }
        return max;
    }
}
