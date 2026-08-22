package com.akto.service.insights.providers;

import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.service.insights.*;

import java.util.*;

/**
 * Token consumption per user, split on-/off-domain by the same per-agent
 * AgentDomainClassifier used in insight 6, against the team median. All-time
 * (UserAnalysisData has no per-topic timestamp) — a date-scoped version needs an ES
 * aggregation and is intentionally left for a follow-up.
 */
public class OffDomainTokenBurnProvider extends AbstractInsightProvider {

    public OffDomainTokenBurnProvider() { super(InsightId.OFF_DOMAIN_TOKEN_BURN, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        if (bundle.userAnalysis.isEmpty()) {
            r.setStatus(InsightResult.Status.NO_DATA.name());
            r.setHeadline("No token usage data available");
            return r;
        }

        Map<String, Long> totalTokensByUser = new HashMap<>();
        Map<String, Long> offDomainTokensByUser = new HashMap<>();

        for (UserAnalysisData d : bundle.userAnalysis) {
            if (d.getUserName() == null) continue;
            long tokens = d.getTotalInputTokens() + d.getTotalOutputTokens();
            totalTokensByUser.merge(d.getUserName(), tokens, Long::sum);

            if (scope != Scope.DETAIL || d.getTopicHierarchy() == null || d.getTopicHierarchy().isEmpty()) continue;
            String agent = d.getId() != null ? d.getId().getServiceId() : null;
            String description = InsightUtil.agentDescription(bundle, agent);
            if (description == null) continue;

            long rowTotalInteractions = 0, rowOffInteractions = 0;
            List<String> domains = new ArrayList<>(d.getTopicHierarchy().keySet());
            Map<String, String> classification = InsightClassificationHelper.classifyDomains(description, domains);
            for (Map.Entry<String, Map<String, Integer>> e : d.getTopicHierarchy().entrySet()) {
                int c = 0;
                for (int v : e.getValue().values()) c += v;
                rowTotalInteractions += c;
                if (!"ON".equals(classification.get(e.getKey()))) rowOffInteractions += c;
            }
            // Apportion this row's tokens by its own off-domain interaction share — the closest
            // honest estimate available, since tokens aren't tracked per-topic in UserAnalysisData.
            if (rowTotalInteractions > 0) {
                long offTokens = Math.round(tokens * ((double) rowOffInteractions / rowTotalInteractions));
                offDomainTokensByUser.merge(d.getUserName(), offTokens, Long::sum);
            }
        }

        long teamMedian = median(new ArrayList<>(totalTokensByUser.values()));
        List<Map.Entry<String, Long>> ranked = new ArrayList<>(totalTokensByUser.entrySet());
        ranked.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        r.addMetric(new InsightResult.Metric("teamMedianTokens", "Team median tokens", teamMedian, "tokens", InsightUtil.count(teamMedian, "tokens")));
        if (!ranked.isEmpty()) {
            r.addMetric(new InsightResult.Metric("topUserTokens", "Top user's tokens", ranked.get(0).getValue(), "tokens",
                    InsightUtil.count(ranked.get(0).getValue(), "tokens")));
        }

        r.setMetricsComplete(scope == Scope.DETAIL);
        if (scope != Scope.DETAIL) {
            r.setStatus(InsightResult.Status.PARTIAL.name());
            r.addDataGap(new InsightResult.Gap("AGENT_DESCRIPTION", "DEFERRED_TO_DETAIL", "On-/off-domain token split is computed only when this insight is opened."));
        } else if (offDomainTokensByUser.isEmpty()) {
            r.setStatus(InsightResult.Status.PARTIAL.name());
            r.addDataGap(new InsightResult.Gap("AGENT_DESCRIPTION", "NO_ROWS", "No agent has a description set, so the on-/off-domain split could not be computed."));
        } else {
            r.setStatus(InsightResult.Status.READY.name());
        }
        r.setHeadline(ranked.isEmpty() ? "No token usage data available"
                : "Team median is " + InsightUtil.count(teamMedian, "tokens") + "; top user is " + InsightUtil.count(ranked.get(0).getValue(), "tokens"));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : ranked.subList(0, Math.min(10, ranked.size()))) {
            Map<String, Object> row = new HashMap<>();
            row.put("user", e.getKey());
            row.put("totalTokens", e.getValue());
            row.put("offDomainTokens", offDomainTokensByUser.getOrDefault(e.getKey(), null));
            row.put("team", bundle.teamForUser(e.getKey()));
            rows.add(row);
        }
        r.addEvidence(new InsightResult.Evidence("top_users", "Top users by token volume",
                Arrays.asList("user", "totalTokens", "offDomainTokens", "team"), rows, ranked.size()));

        r.addCta(new InsightResult.Cta("view_llm", "View LLM observability", "NAVIGATE", InsightRoutes.LLM_OBSERVABILITY, new HashMap<>(), true));
        r.addCta(new InsightResult.Cta("view_users", "View users and devices", "NAVIGATE", InsightRoutes.USERS_AND_DEVICES, new HashMap<>(), false));
        return r;
    }

    private long median(List<Long> values) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(mid - 1) + sorted.get(mid)) / 2 : sorted.get(mid);
    }
}
