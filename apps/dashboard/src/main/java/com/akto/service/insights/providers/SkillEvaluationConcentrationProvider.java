package com.akto.service.insights.providers;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.SkillSeverityCount;
import com.akto.service.insights.DataGap;
import com.akto.service.insights.EvidenceRow;
import com.akto.service.insights.EvidenceTable;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightCta;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightMetric;
import com.akto.service.insights.InsightProvider;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightScope;
import com.akto.service.insights.InsightStatus;
import com.akto.service.insights.Loaded;
import com.akto.service.insights.MetricFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Actionable item #3 — "One user, one class, flooding the queue": flag when a single user
 * accounts for most of the skill-evaluation volume. See akto_insights_actionable_items card 3
 * (the nayanantiya / offensive-security worked example).
 *
 * The doc's pseudocode groups by (user x category) and flags a pair > 50% of the bucket, but its
 * own worked example is a user whose 3,793 evaluations span FIVE different skill names — no
 * single (user, skill) pair would cross 50% there, only the user's total would. Both readings are
 * reported: the flag/severity is driven by a user's total share (matches the worked example), and
 * whether the SAME single skill also crosses 50% on its own is called out separately when true
 * (a strictly narrower, stronger signal, since a user's total is always >= any one of their
 * skills' counts).
 */
public class SkillEvaluationConcentrationProvider implements InsightProvider {

    private static final String SKILLS_PREFIX = "/skills/";
    private static final int EVENT_PAGE_LIMIT = 2000;
    private static final int EVIDENCE_ROW_CAP = 20;

    @Override
    public InsightId getInsightId() {
        return InsightId.SKILL_EVALUATION_CONCENTRATION;
    }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, InsightScope scope,
                                  AbstractThreatDetectionAction threatClient) {
        Loaded<List<SkillSeverityCount>> skillLoaded = bundle.getSkillSeverityCounts();
        if (!skillLoaded.isOk()) {
            return failed("Could not load skill evaluation counts: " + skillLoaded.getFailureReason());
        }

        List<SkillSeverityCount> perSkill = skillLoaded.getValue();
        long total = 0;
        long totalCritical = 0;
        for (SkillSeverityCount s : perSkill) {
            total += s.getCritical() + s.getHigh() + s.getMedium() + s.getLow();
            totalCritical += s.getCritical();
        }

        InsightResult result = base();
        InsightMetric totalMetric = new InsightMetric(
                "total_skill_evaluations", "Skill evaluations",
                total, null, "count", MetricFormat.count(total, "evaluation"), null);

        if (total == 0) {
            // Confident zero — the aggregate genuinely has no rows, not an absent signal.
            result.setStatus(InsightStatus.READY.name());
            result.setHeadline("No skill evaluations detected in this window.");
            result.setMetrics(Collections.singletonList(totalMetric));
            result.setMetricsComplete(true);
            result.setDataGaps(new ArrayList<>());
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        InsightMetric skillCountMetric = new InsightMetric(
                "distinct_skill_count", "Distinct skills evaluated",
                perSkill.size(), null, "count",
                MetricFormat.count(perSkill.size(), "skill"), null);
        List<InsightMetric> baseMetrics = new ArrayList<>(Arrays.asList(totalMetric, skillCountMetric));

        if (scope == InsightScope.LIST) {
            result.setStatus(InsightStatus.PARTIAL.name());
            result.setHeadline(MetricFormat.count(total, "evaluation") + " across "
                    + MetricFormat.count(perSkill.size(), "skill")
                    + "; per-user concentration requires the detail view.");
            result.setMetrics(baseMetrics);
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new DataGap(
                    "PROVIDER", "DEFERRED_TO_DETAIL",
                    "Per-user concentration requires opening this insight's detail view.")));
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        // DETAIL: page raw skill-evaluation events (x-skill-eval-mode: only) to attribute by actor.
        List<DashboardMaliciousEvent> events;
        try {
            events = threatClient.fetchAllMaliciousEvents(
                    ctx.getStartTs(), ctx.getEndTs(), EVENT_PAGE_LIMIT, null, "only");
        } catch (Exception e) {
            events = new ArrayList<>();
        }

        // get_skill_severity_counts (this insight's LIST-scope aggregate) has no status filter and
        // counts every status; list_malicious_requests defaults to ACTIVE-only. So a small gap
        // between `total` and the paged count is expected, not a bug — only a fully empty page
        // against a known-nonzero aggregate is treated as suspicious here.
        if (events.isEmpty()) {
            result.setStatus(InsightStatus.PARTIAL.name());
            result.setHeadline(MetricFormat.count(total, "evaluation") + " across "
                    + MetricFormat.count(perSkill.size(), "skill") + ".");
            result.setMetrics(baseMetrics);
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new DataGap(
                    "THREAT_BACKEND", "NO_ROWS",
                    "Per-user attribution unavailable — no ACTIVE-status skill-evaluation events were "
                            + "returned, though " + total + " are recorded in the all-status aggregate count.")));
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        Map<String, Long> perActor = new HashMap<>();
        Map<String, Long> perActorCritical = new HashMap<>();
        Map<String, Map<String, Long>> perActorSkill = new HashMap<>();
        long topPairCount = 0;
        String topPairSkill = null;

        for (DashboardMaliciousEvent event : events) {
            String skillName = extractSkillName(event.getUrl());
            if (skillName == null) {
                continue; // defensive — shouldn't happen with the skill-eval-mode header set
            }
            String actor = event.getActor();
            if (actor == null || actor.trim().isEmpty()) {
                actor = "(unattributed)";
            }
            perActor.merge(actor, 1L, Long::sum);
            if ("CRITICAL".equalsIgnoreCase(event.getSeverity())) {
                perActorCritical.merge(actor, 1L, Long::sum);
            }
            Map<String, Long> skillMap = perActorSkill.computeIfAbsent(actor, k -> new HashMap<>());
            long newCount = skillMap.merge(skillName, 1L, Long::sum);
            if (newCount > topPairCount) {
                topPairCount = newCount;
                topPairSkill = skillName;
            }
        }

        long observedTotal = events.size();
        String topActor = null;
        long topActorCount = 0;
        for (Map.Entry<String, Long> e : perActor.entrySet()) {
            if (e.getValue() > topActorCount) {
                topActorCount = e.getValue();
                topActor = e.getKey();
            }
        }

        double topActorFraction = observedTotal > 0 ? (double) topActorCount / observedTotal : 0.0;
        double topPairFraction = observedTotal > 0 ? (double) topPairCount / observedTotal : 0.0;
        long topActorCriticalCount = topActor != null ? perActorCritical.getOrDefault(topActor, 0L) : 0;

        InsightMetric topUserShare = new InsightMetric(
                "top_user_share", "Top user's share of evaluations seen",
                topActorCount, observedTotal, "count",
                MetricFormat.ofTotal(topActorCount, observedTotal), null);
        InsightMetric topUserPercent = new InsightMetric(
                "top_user_percent", "Top user's percent of evaluations seen",
                Math.round(topActorFraction * 100), null, "percent",
                MetricFormat.percent(topActorFraction), null);
        List<InsightMetric> metrics = new ArrayList<>(baseMetrics);
        metrics.add(topUserShare);
        metrics.add(topUserPercent);

        if (totalCritical > 0) {
            metrics.add(new InsightMetric(
                    "top_user_critical_share", "Top user's share of Critical-severity evaluations",
                    topActorCriticalCount, totalCritical, "count",
                    MetricFormat.ofTotal(topActorCriticalCount, totalCritical), null));
        }

        boolean userFlagged = topActorFraction > 0.5;
        boolean pairAlsoFlagged = topPairFraction > 0.5;

        String severity;
        if (userFlagged) {
            severity = "HIGH";
        } else if (topActorFraction > 0.25) {
            severity = "MEDIUM";
        } else {
            severity = "LOW";
        }

        StringBuilder headline = new StringBuilder();
        if (userFlagged) {
            headline.append(MetricFormat.ofTotal(topActorCount, observedTotal))
                    .append(" (").append(MetricFormat.percent(topActorFraction))
                    .append(") of skill evaluations seen come from a single user");
            if (topActor != null && !"(unattributed)".equals(topActor)) {
                headline.append(" (").append(topActor).append(")");
            }
            headline.append(".");
            if (pairAlsoFlagged) {
                headline.append(" All from a single skill: ").append(topPairSkill).append(".");
            }
        } else {
            headline.append("No single user accounts for more than half of ")
                    .append(MetricFormat.count(observedTotal, "skill evaluation")).append(" seen.");
        }

        List<String> caveats = new ArrayList<>();
        caveats.add("\"User\" may be a device identifier rather than a resolved person — confirm identity before acting.");
        if (observedTotal == EVENT_PAGE_LIMIT) {
            caveats.add("Evaluations examined are capped at " + EVENT_PAGE_LIMIT
                    + " for this call; the true concentration may differ if more exist.");
        }

        List<EvidenceTable> evidence = new ArrayList<>();
        if (topActor != null) {
            Map<String, Long> topActorSkills = perActorSkill.getOrDefault(topActor, Collections.emptyMap());
            List<Map.Entry<String, Long>> sortedSkills = topActorSkills.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(EVIDENCE_ROW_CAP)
                    .collect(Collectors.toList());
            List<EvidenceRow> rows = new ArrayList<>();
            for (Map.Entry<String, Long> e : sortedSkills) {
                Map<String, Object> raw = new HashMap<>();
                raw.put("actor", topActor);
                raw.put("skillName", e.getKey());
                raw.put("count", e.getValue());
                rows.add(new EvidenceRow(
                        Arrays.asList(topActor, e.getKey(), MetricFormat.count(e.getValue(), "evaluation")), raw));
            }
            evidence.add(new EvidenceTable("top_user_skill_breakdown", "Top user's skill breakdown",
                    Arrays.asList("User", "Skill", "Evaluations"), rows, topActorSkills.size()));
        }

        result.setStatus(InsightStatus.READY.name());
        result.setHeadline(headline.toString());
        result.setSeverity(severity);
        result.setMetrics(metrics);
        result.setMetricsComplete(true);
        result.setDataGaps(new ArrayList<>());
        result.setCaveats(caveats);
        result.setEvidence(evidence);
        result.setCtas(buildCtas(topActor, perActorSkill.getOrDefault(topActor, Collections.emptyMap()).keySet()));
        return result;
    }

    /** Everything after "/skills/" — deliberately the whole remainder, matching ThreatActorService's
     *  own $substrCP extraction, not just the first path segment. */
    private static String extractSkillName(String url) {
        if (url == null || !url.startsWith(SKILLS_PREFIX)) {
            return null;
        }
        String rest = url.substring(SKILLS_PREFIX.length());
        return rest.isEmpty() ? null : rest;
    }

    private InsightResult base() {
        InsightResult r = new InsightResult();
        r.setInsightId(getInsightId().name());
        r.setTitle(getInsightId().getDefaultTitle());
        r.setCategory(getInsightId().getCategory().name());
        r.setCaveats(new ArrayList<>());
        return r;
    }

    private InsightResult failed(String reason) {
        InsightResult r = InsightResult.noData(getInsightId(), reason);
        r.setDataGaps(new ArrayList<>(Collections.singletonList(
                new DataGap("THREAT_BACKEND", "REQUEST_FAILED", reason))));
        return r;
    }

    private static List<InsightCta> buildCtas(String actor, Set<String> skillNames) {
        if (actor == null) {
            return new ArrayList<>();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("actor", actor);
        params.put("skillNames", new ArrayList<>(new HashSet<>(skillNames)));

        List<InsightCta> ctas = new ArrayList<>();
        ctas.add(new InsightCta("create_exception_for_user_skill", "Create exception for user + skill category",
                "BULK_ACTION", "/dashboard/guardrails/policies", params, true));
        ctas.add(new InsightCta("confirm_role_with_manager", "Confirm role with manager",
                "NAVIGATE", "/dashboard/observe/users-and-devices",
                Collections.singletonMap("username", actor), false));
        ctas.add(new InsightCta("view_skills", "View skills",
                "NAVIGATE", "/dashboard/observe/audit", params, false));
        return ctas;
    }
}
