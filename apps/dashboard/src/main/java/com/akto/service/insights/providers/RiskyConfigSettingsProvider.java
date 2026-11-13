package com.akto.service.insights.providers;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.service.guardrails.AtlasGuardrailUtils;
import com.akto.service.insights.AbstractInsightProvider;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightRoutes;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RiskyConfigSettingsProvider extends AbstractInsightProvider {

    private static final int EVIDENCE_ROWS = 10;

    public RiskyConfigSettingsProvider() { super(InsightId.RISKY_CONFIG_SETTINGS, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        List<DashboardMaliciousEvent> events = AtlasGuardrailUtils.currentPeriodEvents(bundle);
        if (events == null) return failed("THREAT_BACKEND", "Could not load violations");

        Map<String, Setting> settings = new HashMap<>();
        for (DashboardMaliciousEvent e : AtlasGuardrailUtils.collapseConfigFindings(events)) {
            if (!AtlasGuardrailUtils.isConfigFinding(e) || !AtlasGuardrailUtils.isOpen(e)) continue;
            String policy = StringUtils.defaultString(e.getFilterId());
            settings.computeIfAbsent(policy + "|" + e.getUrl(), k -> new Setting(e.getUrl(), policy)).add(e, bundle);
        }

        InsightResult r = skeleton();
        if (settings.isEmpty()) {
            r.setHeadline("No open risky config settings in this period.");
            return r;
        }
        List<Setting> ranked = settings.values().stream().sorted(ViolationGroup.WORST_FIRST).collect(Collectors.toList());
        Setting worst = ranked.get(0);

        r.setStatus(InsightResult.Status.READY.name());
        r.setSeverity(worst.severity);
        r.setTitle(settingTitle(worst.name));
        r.setHeadline(worst.employees() + (worst.policy.isEmpty() ? "" : " · " + worst.policy) + " · " + worst.decision());
        worst.addMetrics(r);
        r.addEvidence(new InsightResult.Evidence("settings", "Risky agent config settings",
                Arrays.asList("name", "policy", "violations", "employees", "devices", "severity", "decision"),
                ranked.stream().limit(EVIDENCE_ROWS).map(Setting::evidenceRow).collect(Collectors.toList()), ranked.size()));

        Map<String, Object> review = new HashMap<>();
        review.put("eventStatus", AtlasGuardrailUtils.ACTIVITY_TAB_MISCONFIGURED_SETTINGS);
        review.put("url", worst.name);
        if (!worst.policy.isEmpty()) review.put("latestAttack", worst.policy);
        r.addCta(new InsightResult.Cta("review_violations", "Review", "NAVIGATE", InsightRoutes.THREAT_ACTIVITY, review, true));
        return r;
    }

    private static String settingTitle(String url) {
        int at = url == null ? -1 : url.indexOf(AtlasGuardrailUtils.CONFIG_ENDPOINT_SEGMENT);
        if (at < 0) return "Risky setting: " + url;
        String agent = StringUtils.capitalize(StringUtils.strip(url.substring(0, at), "/"));
        String setting = url.substring(at + AtlasGuardrailUtils.CONFIG_ENDPOINT_SEGMENT.length());
        return "Risky " + (agent.isEmpty() ? "" : agent + " ") + "setting: " + setting;
    }

    private static class Setting extends ViolationGroup {
        private final String policy;

        private Setting(String url, String policy) {
            super(url);
            this.policy = policy;
        }

        @Override
        Map<String, Object> evidenceRow() {
            Map<String, Object> row = super.evidenceRow();
            row.put("policy", policy);
            return row;
        }
    }
}
