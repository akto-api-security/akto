package com.akto.service.insights.providers;

import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.service.insights.*;

import java.util.*;

/**
 * The small set of users responsible for most token volume and harmful-topic activity.
 * Reports the top-20%-of-users share (a fixed cohort, unlike a "users needed to reach 80%"
 * threshold search, which degenerates for small or near-uniform user counts) and how much
 * of the account's harmful-topic activity is concentrated in that same cohort.
 */
public class ExposureConcentrationProvider extends AbstractInsightProvider {

    public ExposureConcentrationProvider() { super(InsightId.EXPOSURE_CONCENTRATION, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        if (bundle.userAnalysis.isEmpty()) {
            r.setStatus(InsightResult.Status.NO_DATA.name());
            r.setHeadline("No user activity data available");
            return r;
        }

        Map<String, Long> tokensByUser = new HashMap<>();
        Map<String, Integer> harmfulHitsByUser = new HashMap<>();
        Map<String, String> topHarmfulTopicByUser = new HashMap<>();
        Map<String, String> topHarmfulReasonByUser = new HashMap<>();

        for (UserAnalysisData d : bundle.userAnalysis) {
            if (d.getUserName() == null || d.getUserName().isEmpty()) continue;
            String user = d.getUserName();
            tokensByUser.merge(user, d.getTotalInputTokens() + d.getTotalOutputTokens(), Long::sum);

            int userHarmfulHits = 0;
            String dominantTopic = null;
            String dominantReason = null;
            int dominantCount = -1;
            Map<String, Object> harmfulTopics = d.getHarmfulTopics();
            if (harmfulTopics != null) {
                for (Map.Entry<String, Object> e : harmfulTopics.entrySet()) {
                    Map<String, Object> topicData = asMap(e.getValue());
                    if (topicData == null) continue;
                    int count = asInt(topicData.get("count"));
                    userHarmfulHits += count;
                    if (count > dominantCount) {
                        dominantCount = count;
                        dominantTopic = e.getKey();
                        Object reason = topicData.get("lastReason");
                        dominantReason = reason != null ? String.valueOf(reason) : null;
                    }
                }
            }
            if (userHarmfulHits > 0) {
                harmfulHitsByUser.merge(user, userHarmfulHits, Integer::sum);
                if (dominantTopic != null) {
                    topHarmfulTopicByUser.put(user, dominantTopic);
                    topHarmfulReasonByUser.put(user, dominantReason);
                }
            }
        }

        List<Map.Entry<String, Long>> ranked = new ArrayList<>(tokensByUser.entrySet());
        ranked.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        long totalTokens = 0;
        for (long v : tokensByUser.values()) totalTokens += v;

        int totalHarmfulHits = 0;
        for (int v : harmfulHitsByUser.values()) totalHarmfulHits += v;

        int topCohortSize = Math.max(1, (int) Math.ceil(ranked.size() * 0.2));

        long cumulativeTokens = 0;
        long topCohortTokens = 0;
        int topCohortHarmfulHits = 0;
        List<String> topCohortUsers = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Map.Entry<String, Long> e = ranked.get(i);
            String user = e.getKey();
            cumulativeTokens += e.getValue();
            int harmfulHits = harmfulHitsByUser.getOrDefault(user, 0);
            if (i < topCohortSize) {
                topCohortTokens += e.getValue();
                topCohortHarmfulHits += harmfulHits;
                topCohortUsers.add(user);
            }
            if (rows.size() < 10) {
                Map<String, Object> row = new HashMap<>();
                row.put("user", user);
                row.put("tokens", e.getValue());
                row.put("cumulativePercent", InsightUtil.percent(totalTokens > 0 ? (double) cumulativeTokens / totalTokens : 0));
                row.put("harmfulHits", harmfulHits);
                row.put("topHarmfulTopic", topHarmfulTopicByUser.getOrDefault(user, "-"));
                row.put("topHarmfulReason", topHarmfulReasonByUser.getOrDefault(user, "-"));
                rows.add(row);
            }
        }

        double topCohortTokenShare = totalTokens > 0 ? (double) topCohortTokens / totalTokens : 0;
        double top1Share = totalTokens > 0 && !ranked.isEmpty() ? (double) ranked.get(0).getValue() / totalTokens : 0;
        double harmfulShareInTopCohort = totalHarmfulHits > 0 ? (double) topCohortHarmfulHits / totalHarmfulHits : 0;
        int usersWithHarmfulActivity = harmfulHitsByUser.size();

        r.addMetric(new InsightResult.Metric("totalUsers", "Users with activity", ranked.size(), "count", InsightUtil.count(ranked.size(), "users")));
        r.addMetric(new InsightResult.Metric("top20PctTokenShare", "Token share held by the top 20% of users", topCohortTokenShare, "percent",
                InsightUtil.count(topCohortSize, "users") + " hold " + InsightUtil.percent(topCohortTokenShare) + " of token volume"));
        r.addMetric(new InsightResult.Metric("top1Share", "Top user's share of total tokens", top1Share, "percent", InsightUtil.percent(top1Share)));
        r.addMetric(new InsightResult.Metric("usersWithHarmfulActivity", "Users with harmful-topic activity", usersWithHarmfulActivity, "count",
                InsightUtil.count(usersWithHarmfulActivity, "users")));
        r.addMetric(new InsightResult.Metric("harmfulShareInTopCohort", "Harmful-topic hits coming from the top 20% by volume", harmfulShareInTopCohort,
                "percent", InsightUtil.percent(harmfulShareInTopCohort)));

        r.setStatus(InsightResult.Status.PARTIAL.name());
        r.addDataGap(new InsightResult.Gap("DEVICE_IDENTITY", "NOT_CONFIGURED",
                "Concentration is measured by token volume and harmful-topic hits per user; sensitive-data exposure concentration per user requires device attribution and is not included."));

        r.setHeadline(usersWithHarmfulActivity == 0
                ? InsightUtil.count(topCohortSize, "users") + " (top 20%) hold " + InsightUtil.percent(topCohortTokenShare) + " of AI token volume"
                : InsightUtil.count(topCohortSize, "users") + " (top 20%) hold " + InsightUtil.percent(topCohortTokenShare) + " of AI token volume, "
                        + InsightUtil.count(usersWithHarmfulActivity, "users") + " flagged for harmful-topic activity");

        if (usersWithHarmfulActivity > 0) {
            r.setSeverity(harmfulShareInTopCohort >= 0.5 ? "HIGH" : "MEDIUM");
            r.setConcern("AI usage — and the harmful-topic activity that comes with it — is concentrated in a small group of users rather than spread evenly.");
            r.setImpact("A concentrated cohort is both your biggest single point of failure and your highest-leverage place to focus review: "
                    + InsightUtil.percent(harmfulShareInTopCohort) + " of all harmful-topic hits trace back to just the top 20% of users by volume.");
            r.setRemediation("Review the users below individually — check what they're actually using AI for and whether their access needs tighter guardrails.");
        } else if (topCohortTokenShare >= 0.7) {
            r.setSeverity("LOW");
            r.setConcern("Token volume is heavily concentrated in a small group of users, though none are flagged for harmful-topic activity.");
            r.setImpact("If one of these heavy users' credentials or agent were compromised, the blast radius would be disproportionately large.");
            r.setRemediation("No action needed today — worth keeping an eye on this cohort as usage grows.");
        }

        r.addEvidence(new InsightResult.Evidence("top_users", "Top users by token volume and harmful-topic activity",
                Arrays.asList("user", "tokens", "cumulativePercent", "harmfulHits", "topHarmfulTopic", "topHarmfulReason"), rows, ranked.size()));

        r.addCta(new InsightResult.Cta("view_users", "View users and devices", "NAVIGATE", InsightRoutes.USERS_AND_DEVICES,
                InsightUtil.usersAndDevicesFilterParams(topCohortUsers), true));
        r.addCta(new InsightResult.Cta("view_llm", "View session-level AI usage (LLM Observability)", "NAVIGATE", InsightRoutes.LLM_OBSERVABILITY, new HashMap<>(), false));
        return r;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    private static int asInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try { return Integer.parseInt((String) o); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
