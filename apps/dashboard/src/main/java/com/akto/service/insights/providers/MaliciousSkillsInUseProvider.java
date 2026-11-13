package com.akto.service.insights.providers;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.service.guardrails.AtlasGuardrailUtils;
import com.akto.service.insights.AbstractInsightProvider;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightRoutes;
import com.akto.service.insights.InsightUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MaliciousSkillsInUseProvider extends AbstractInsightProvider {

    private static final String MALICIOUS_SKILL_FILTER_ID = "malicious_skill_detected";
    private static final int EVIDENCE_ROWS = 10;

    public MaliciousSkillsInUseProvider() { super(InsightId.MALICIOUS_SKILLS_IN_USE, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        List<DashboardMaliciousEvent> events = AtlasGuardrailUtils.currentPeriodEvents(bundle);
        if (events == null) return failed("THREAT_BACKEND", "Could not load violations");

        Map<String, ViolationGroup> skills = new HashMap<>();
        for (DashboardMaliciousEvent e : events) {
            if (e == null || !AtlasGuardrailUtils.isOpen(e) || !MALICIOUS_SKILL_FILTER_ID.equals(e.getFilterId())) continue;
            String skill = SkillEvaluationConcentrationProvider.extractSkillName(e.getUrl());
            if (skill == null) continue;
            skills.computeIfAbsent(skill, ViolationGroup::new).add(e, bundle);
        }

        InsightResult r = skeleton();
        if (skills.isEmpty()) {
            r.setHeadline("No open malicious skill findings in this period.");
            return r;
        }
        List<ViolationGroup> ranked = skills.values().stream().sorted(ViolationGroup.WORST_FIRST).collect(Collectors.toList());
        ViolationGroup worst = ranked.get(0);

        r.setStatus(InsightResult.Status.READY.name());
        r.setSeverity(worst.severity);
        r.setTitle("Malicious skill " + worst.name);
        r.setHeadline(InsightUtil.count(worst.count, "open findings") + " · " + worst.employees() + " · " + worst.decision());
        worst.addMetrics(r);
        r.addEvidence(new InsightResult.Evidence("skills", "Malicious skills in use",
                Arrays.asList("name", "violations", "employees", "devices", "severity", "decision"),
                ranked.stream().limit(EVIDENCE_ROWS).map(ViolationGroup::evidenceRow).collect(Collectors.toList()), ranked.size()));

        Map<String, Object> review = new HashMap<>();
        review.put("eventStatus", AtlasGuardrailUtils.ACTIVITY_TAB_SKILLS_EVALUATIONS);
        review.put("latestAttack", MALICIOUS_SKILL_FILTER_ID);
        review.put("url", SkillEvaluationConcentrationProvider.SKILLS_PREFIX + worst.name);
        r.addCta(new InsightResult.Cta("review_violations", "Review", "NAVIGATE", InsightRoutes.THREAT_ACTIVITY, review, true));
        return r;
    }
}
