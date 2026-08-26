package com.akto.service.insights.providers;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.SkillSeverityCount;
import com.akto.service.insights.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
public class SkillEvaluationConcentrationProvider extends AbstractInsightProvider {

    private static final String SKILLS_PREFIX = "/skills/";
    private static final int EVENT_PAGE_LIMIT = 2000;
    private static final int EVIDENCE_ROW_CAP = 20;

    public SkillEvaluationConcentrationProvider() { super(InsightId.SKILL_EVALUATION_CONCENTRATION, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        // skillSeverityCounts is a threat-backend aggregate (like hostSeverityCounts/subCategoryCounts)
        // and the bundle never returns null for it — a failed call is signalled by
        // threatBackendAvailable instead, so an empty list here can't be told apart from a real zero
        // unless that flag is also checked.
        if (!bundle.threatBackendAvailable) {
            return failed("THREAT_BACKEND", "Could not load skill evaluation counts");
        }
        List<SkillSeverityCount> perSkill = bundle.skillSeverityCounts;
        long total = 0;
        long totalCritical = 0;
        for (SkillSeverityCount s : perSkill) {
            total += s.getCritical() + s.getHigh() + s.getMedium() + s.getLow();
            totalCritical += s.getCritical();
        }

        InsightResult result = skeleton();
        InsightResult.Metric totalMetric = new InsightResult.Metric(
                "total_skill_evaluations", "Skill evaluations",
                total, null, "count", InsightUtil.count(total, "evaluations"), null);

        if (total == 0) {
            // Confident zero — the aggregate genuinely has no rows, not an absent signal.
            result.setStatus(InsightResult.Status.READY.name());
            result.setHeadline("No skill evaluations detected in this window.");
            result.setMetrics(Collections.singletonList(totalMetric));
            result.setMetricsComplete(true);
            result.setDataGaps(new ArrayList<>());
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        InsightResult.Metric skillCountMetric = new InsightResult.Metric(
                "distinct_skill_count", "Distinct skills evaluated",
                perSkill.size(), null, "count",
                InsightUtil.count(perSkill.size(), "skills"), null);
        List<InsightResult.Metric> baseMetrics = new ArrayList<>(Arrays.asList(totalMetric, skillCountMetric));

        if (scope == Scope.LIST) {
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(InsightUtil.count(total, "evaluations") + " across "
                    + InsightUtil.count(perSkill.size(), "skills")
                    + "; per-user concentration requires the detail view.");
            result.setMetrics(baseMetrics);
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new InsightResult.Gap(
                    "PROVIDER", "DEFERRED_TO_DETAIL",
                    "Per-user concentration requires opening this insight's detail view.")));
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        // DETAIL: page raw skill-evaluation events (x-skill-eval-mode: only) to attribute by actor.
        List<DashboardMaliciousEvent> events = bundle.fetchViolationEvents(scope, EVENT_PAGE_LIMIT, null, "only");
        if (events == null) events = new ArrayList<>();

        // get_skill_severity_counts (this insight's LIST-scope aggregate) has no status filter and
        // counts every status; list_malicious_requests defaults to ACTIVE-only. So a small gap
        // between `total` and the paged count is expected, not a bug — only a fully empty page
        // against a known-nonzero aggregate is treated as suspicious here.
        if (events.isEmpty()) {
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(InsightUtil.count(total, "evaluations") + " across "
                    + InsightUtil.count(perSkill.size(), "skills") + ".");
            result.setMetrics(baseMetrics);
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new InsightResult.Gap(
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

        InsightResult.Metric topUserShare = new InsightResult.Metric(
                "top_user_share", "Top user's share of evaluations seen",
                topActorCount, observedTotal, "count",
                InsightUtil.ofTotal(topActorCount, observedTotal, "evaluations"), null);
        InsightResult.Metric topUserPercent = new InsightResult.Metric(
                "top_user_percent", "Top user's percent of evaluations seen",
                Math.round(topActorFraction * 100), null, "percent",
                InsightUtil.percent(topActorFraction), null);
        List<InsightResult.Metric> metrics = new ArrayList<>(baseMetrics);
        metrics.add(topUserShare);
        metrics.add(topUserPercent);

        if (totalCritical > 0) {
            metrics.add(new InsightResult.Metric(
                    "top_user_critical_share", "Top user's share of Critical-severity evaluations",
                    topActorCriticalCount, totalCritical, "count",
                    InsightUtil.ofTotal(topActorCriticalCount, totalCritical, "evaluations"), null));
        }

        boolean userFlagged = topActorFraction > 0.5;
        boolean pairAlsoFlagged = topPairFraction > 0.5;
        // Dominating volume and dominating Critical-severity evaluations are different risks — a
        // user responsible for most of the account's Critical-severity evaluations specifically is
        // worse than one responsible for a lot of routine ones, even if their raw volume share
        // doesn't cross the HIGH threshold above.
        double topActorCriticalFraction = totalCritical > 0 ? (double) topActorCriticalCount / totalCritical : 0.0;
        boolean userDominatesCritical = totalCritical > 0 && topActorCriticalFraction > 0.5;

        String severity;
        if (userDominatesCritical) {
            severity = "CRITICAL";
        } else if (userFlagged) {
            severity = "HIGH";
        } else if (topActorFraction > 0.25) {
            severity = "MEDIUM";
        } else {
            severity = "LOW";
        }

        StringBuilder headline = new StringBuilder();
        if (userFlagged || userDominatesCritical) {
            headline.append(InsightUtil.ofTotal(topActorCount, observedTotal, "evaluations"))
                    .append(" (").append(InsightUtil.percent(topActorFraction))
                    .append(") of skill evaluations seen come from a single user");
            if (topActor != null && !"(unattributed)".equals(topActor)) {
                headline.append(" (").append(topActor).append(")");
            }
            headline.append(".");
            if (pairAlsoFlagged) {
                headline.append(" All from a single skill: ").append(topPairSkill).append(".");
            }
            if (userDominatesCritical) {
                headline.append(" That same user accounts for ")
                        .append(InsightUtil.percent(topActorCriticalFraction))
                        .append(" of all Critical-severity evaluations.");
            }
        } else {
            headline.append("No single user accounts for more than half of ")
                    .append(InsightUtil.count(observedTotal, "skill evaluations")).append(" seen.");
        }

        List<String> caveats = new ArrayList<>();
        caveats.add("\"User\" may be a device identifier rather than a resolved person — confirm identity before acting.");
        if (observedTotal == EVENT_PAGE_LIMIT) {
            caveats.add("Evaluations examined are capped at " + EVENT_PAGE_LIMIT
                    + " for this call; the true concentration may differ if more exist.");
        }

        String concern = null;
        String impact = null;
        String remediation = null;
        if (userFlagged || userDominatesCritical) {
            concern = String.format(java.util.Locale.US,
                    "\"%s\" accounts for %s of all skill evaluations seen%s.",
                    topActor, InsightUtil.percent(topActorFraction),
                    pairAlsoFlagged ? " — all from a single skill, \"" + topPairSkill + "\"" : "");
            impact = userDominatesCritical
                    ? "One user or automation dominating Critical-severity evaluations specifically is either a "
                            + "compromised account, a misconfigured automation, or a role with far more access than "
                            + "it should need."
                    : "This much volume from a single source is either a legitimate automation that should be "
                            + "identified as such, or a single account with more reach than expected.";
            remediation = "Confirm with this user's manager (or the automation's owner) whether this level of "
                    + "activity is expected; if it's an automation, tag it as one rather than leaving it looking "
                    + "like one person's manual activity.";
        }

        List<InsightResult.Evidence> evidence = new ArrayList<>();
        if (topActor != null) {
            Map<String, Long> topActorSkills = perActorSkill.getOrDefault(topActor, Collections.emptyMap());
            List<Map.Entry<String, Long>> sortedSkills = topActorSkills.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(EVIDENCE_ROW_CAP)
                    .collect(Collectors.toList());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map.Entry<String, Long> e : sortedSkills) {
                Map<String, Object> row = new HashMap<>();
                row.put("User", topActor);
                row.put("Skill", e.getKey());
                row.put("Evaluations", InsightUtil.count(e.getValue(), "evaluations"));
                rows.add(row);
            }
            evidence.add(new InsightResult.Evidence("top_user_skill_breakdown", "Top user's skill breakdown",
                    Arrays.asList("User", "Skill", "Evaluations"), rows, topActorSkills.size()));
        }

        result.setStatus(InsightResult.Status.READY.name());
        result.setHeadline(headline.toString());
        result.setSeverity(severity);
        result.setMetrics(metrics);
        result.setMetricsComplete(true);
        result.setDataGaps(new ArrayList<>());
        result.setCaveats(caveats);
        result.setConcern(concern);
        result.setImpact(impact);
        result.setRemediation(remediation);
        result.setEvidence(evidence);
        result.setCtas(buildCtas(topActor));
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


    private static List<InsightResult.Cta> buildCtas(String actor) {
        if (actor == null) {
            return new ArrayList<>();
        }
        // Neither Guardrail Policies nor Audit reads a user/skill query param today — there's no
        // "create an exception for this user+skill" or "filter Audit to this user's skills" action
        // to deep-link into yet, so these stay plain navigates to the right page rather than
        // passing skillNames/actor params the destination silently ignores.
        List<InsightResult.Cta> ctas = new ArrayList<>();
        ctas.add(new InsightResult.Cta("create_exception_for_user_skill", "Create exception for user + skill category",
                "NAVIGATE", "/dashboard/guardrails/policies", Collections.emptyMap(), true));
        ctas.add(new InsightResult.Cta("confirm_role_with_manager", "Confirm role with manager",
                "NAVIGATE", "/dashboard/observe/users-and-devices",
                InsightUtil.usersAndDevicesFilterParams(Collections.singletonList(actor)), false));
        ctas.add(new InsightResult.Cta("view_skills", "View this user's violations",
                "NAVIGATE", "/dashboard/guardrails/violations",
                Collections.singletonMap("user", actor), false));
        return ctas;
    }
}
