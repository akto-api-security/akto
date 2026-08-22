package com.akto.service.insights.providers;

import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.service.insights.*;

import java.util.*;

/** The small set of users responsible for most token/interaction volume — a cumulative-% Pareto. */
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
        for (UserAnalysisData d : bundle.userAnalysis) {
            if (d.getUserName() == null || d.getUserName().isEmpty()) continue;
            tokensByUser.merge(d.getUserName(), d.getTotalInputTokens() + d.getTotalOutputTokens(), Long::sum);
        }

        List<Map.Entry<String, Long>> ranked = new ArrayList<>(tokensByUser.entrySet());
        ranked.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        long total = 0;
        for (Long v : tokensByUser.values()) total += v;

        int usersFor80Pct = 0;
        long cumulative = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : ranked) {
            cumulative += e.getValue();
            double cumPct = total > 0 ? (double) cumulative / total : 0;
            if (usersFor80Pct == 0 && cumPct >= 0.8) usersFor80Pct = rows.size() + 1;
            if (rows.size() < 10) {
                Map<String, Object> row = new HashMap<>();
                row.put("user", e.getKey());
                row.put("tokens", e.getValue());
                row.put("cumulativePercent", InsightUtil.percent(cumPct));
                rows.add(row);
            }
        }
        if (usersFor80Pct == 0) usersFor80Pct = ranked.size();

        double top1Share = total > 0 && !ranked.isEmpty() ? (double) ranked.get(0).getValue() / total : 0;

        r.addMetric(new InsightResult.Metric("totalUsers", "Users with activity", ranked.size(), "count", InsightUtil.count(ranked.size(), "users")));
        r.addMetric(new InsightResult.Metric("usersFor80Percent", "Users responsible for 80% of token volume", usersFor80Pct, "count",
                InsightUtil.count(usersFor80Pct, "users")));
        r.addMetric(new InsightResult.Metric("top1Share", "Top user's share of total tokens", top1Share, "percent", InsightUtil.percent(top1Share)));

        r.setStatus(InsightResult.Status.PARTIAL.name());
        r.addDataGap(new InsightResult.Gap("DEVICE_IDENTITY", "NOT_CONFIGURED",
                "Concentration is measured by token volume per user; sensitive-data exposure concentration per user requires device attribution and is not included."));
        r.setHeadline(InsightUtil.count(usersFor80Pct, "users") + " account for 80% of AI token volume");

        r.addEvidence(new InsightResult.Evidence("top_users", "Top users by token volume",
                Arrays.asList("user", "tokens", "cumulativePercent"), rows, ranked.size()));

        r.addCta(new InsightResult.Cta("view_users", "View users and devices", "NAVIGATE", InsightRoutes.USERS_AND_DEVICES, new HashMap<>(), true));
        r.addCta(new InsightResult.Cta("view_llm", "View LLM observability", "NAVIGATE", InsightRoutes.LLM_OBSERVABILITY, new HashMap<>(), false));
        return r;
    }
}
