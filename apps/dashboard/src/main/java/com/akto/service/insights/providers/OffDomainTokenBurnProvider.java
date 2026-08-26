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
        Map<String, String> topOffDomainTopicByUser = new HashMap<>();
        Map<String, Long> topOffDomainTopicTokensByUser = new HashMap<>();
        Map<String, Long> offDomainTokensByTopic = new HashMap<>();

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

                // Apportion further, per off-domain topic, to find what's actually driving the burn.
                for (Map.Entry<String, Map<String, Integer>> e : d.getTopicHierarchy().entrySet()) {
                    if ("ON".equals(classification.get(e.getKey()))) continue;
                    int topicCount = 0;
                    for (int v : e.getValue().values()) topicCount += v;
                    long topicTokens = Math.round(tokens * ((double) topicCount / rowTotalInteractions));
                    offDomainTokensByTopic.merge(e.getKey(), topicTokens, Long::sum);
                    if (topicTokens > topOffDomainTopicTokensByUser.getOrDefault(d.getUserName(), -1L)) {
                        topOffDomainTopicTokensByUser.put(d.getUserName(), topicTokens);
                        topOffDomainTopicByUser.put(d.getUserName(), e.getKey());
                    }
                }
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

        String topOffDomainTopicOverall = null;
        if (!offDomainTokensByTopic.isEmpty()) {
            topOffDomainTopicOverall = offDomainTokensByTopic.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        }
        if (topOffDomainTopicOverall != null) {
            long topTopicTokens = offDomainTokensByTopic.get(topOffDomainTopicOverall);
            r.setSeverity(!ranked.isEmpty() && teamMedian > 0 && ranked.get(0).getValue() > teamMedian * 3 ? "HIGH" : "MEDIUM");
            r.setConcern("\"" + topOffDomainTopicOverall + "\" is the single biggest off-domain topic, burning an estimated " + InsightUtil.count(topTopicTokens, "tokens") + " across affected users.");
            r.setImpact("Off-domain token burn is wasted spend on top of being off-purpose use — it inflates your AI bill without producing anything the business asked for.");
            r.setRemediation("Block the \"" + topOffDomainTopicOverall + "\" topic with a guardrail policy, then check in with the top users below about what they actually need the agent for.");
        } else if (!ranked.isEmpty() && teamMedian > 0 && ranked.get(0).getValue() > teamMedian * 3) {
            r.setSeverity("LOW");
            r.setConcern(ranked.get(0).getKey() + " is using " + InsightUtil.count(ranked.get(0).getValue(), "tokens") + " — more than 3x the team median.");
            r.setImpact("A single user burning outsized token volume is worth understanding, even without an off-domain classification yet.");
            r.setRemediation("Check in with " + ranked.get(0).getKey() + " about what's driving the volume.");
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> topUsers = new ArrayList<>();
        for (Map.Entry<String, Long> e : ranked.subList(0, Math.min(10, ranked.size()))) {
            Map<String, Object> row = new HashMap<>();
            row.put("user", e.getKey());
            row.put("totalTokens", e.getValue());
            row.put("offDomainTokens", offDomainTokensByUser.getOrDefault(e.getKey(), null));
            row.put("topOffDomainTopic", topOffDomainTopicByUser.getOrDefault(e.getKey(), "-"));
            row.put("team", bundle.teamForUser(e.getKey()));
            rows.add(row);
            topUsers.add(e.getKey());
        }
        r.addEvidence(new InsightResult.Evidence("top_users", "Top users by token volume",
                Arrays.asList("user", "totalTokens", "offDomainTokens", "topOffDomainTopic", "team"), rows, ranked.size()));

        r.addCta(new InsightResult.Cta("view_llm", "View session-level AI usage (LLM Observability)", "NAVIGATE", InsightRoutes.LLM_OBSERVABILITY, new HashMap<>(), true));
        r.addCta(new InsightResult.Cta("view_users", "View users and devices", "NAVIGATE", InsightRoutes.USERS_AND_DEVICES,
                InsightUtil.usersAndDevicesFilterParams(topUsers), false));
        if (topOffDomainTopicOverall != null) {
            // blockTopic (not a full GuardrailPolicies-shaped prefill) — InsightDetailView builds
            // the actual deniedTopics/description/etc. prefill from this via the same
            // buildTopicGuardrailPrefillForTopic helper the LLM Observability "Create guardrail"
            // flow already uses, instead of duplicating GuardrailPolicies.DeniedTopic's shape here.
            Map<String, Object> blockTopicParams = new HashMap<>();
            blockTopicParams.put("blockTopic", topOffDomainTopicOverall);
            r.addCta(new InsightResult.Cta("block_topic", "Create guardrail blocking \"" + topOffDomainTopicOverall + "\"",
                    "GUARDRAIL_TEMPLATE", InsightRoutes.GUARDRAIL_POLICIES, blockTopicParams, false));
        }
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
