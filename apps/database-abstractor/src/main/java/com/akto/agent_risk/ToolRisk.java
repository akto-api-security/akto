package com.akto.agent_risk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class ToolRisk implements RiskCategory {

    private static final Pattern[] TOOLS = {
            Pattern.compile("\\b(shell|bash|terminal|exec)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(filesystem|file[_ ]?write|unlink|rmdir)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(db[_ ]?write|mongodb|postgres|sql)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(browser|puppeteer|playwright)\\b", Pattern.CASE_INSENSITIVE)
    };
    private static final String[] TOOL_NAMES = { "shell", "filesystem", "db.write", "browser" };

    @Override
    public String id() {
        return "tool";
    }

    @Override
    public void apply(RiskContext ctx, AgentRiskScore score) {
        String tools = ctx == null ? "" : ctx.getToolFingerprint();
        if (tools == null || tools.isEmpty()) {
            tools = fingerprint(ctx == null ? null : ctx.getRawText());
        }
        score.setToolFingerprint(tools);
        int risk = 0;
        if (tools.contains("shell")) {
            risk = Math.max(risk, 85);
        }
        if (tools.contains("filesystem")) {
            risk = Math.max(risk, 70);
        }
        if (tools.contains("db.write")) {
            risk = Math.max(risk, 65);
        }
        if (tools.contains("browser")) {
            risk = Math.max(risk, 40);
        }
        score.setToolRisk(RiskMath.clamp(risk));
    }

    @Override
    public boolean stale(RiskContext ctx, AgentRiskScore other) {
        if (other == null) {
            return true;
        }
        String current = ctx == null || ctx.getToolFingerprint() == null ? "" : ctx.getToolFingerprint();
        String cached = other.getToolFingerprint() == null ? "" : other.getToolFingerprint();
        return !current.equals(cached);
    }

    static String fingerprint(String text) {
        List<String> found = new ArrayList<>();
        if (text != null) {
            for (int i = 0; i < TOOLS.length; i++) {
                if (TOOLS[i].matcher(text).find()) {
                    found.add(TOOL_NAMES[i]);
                }
            }
        }
        Collections.sort(found);
        return String.join(",", found);
    }
}
