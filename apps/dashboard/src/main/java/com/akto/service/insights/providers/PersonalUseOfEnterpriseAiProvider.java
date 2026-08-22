package com.akto.service.insights.providers;

import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.service.insights.*;

import java.util.*;

/**
 * Share of interactions per agent whose topic domain doesn't fit that agent's own
 * description (ApiCollection.description), judged by a cached AgentDomainClassifier
 * call, DETAIL scope only. All-time (UserAnalysisData has no per-topic timestamp).
 */
public class PersonalUseOfEnterpriseAiProvider extends AbstractInsightProvider {

    public PersonalUseOfEnterpriseAiProvider() { super(InsightId.PERSONAL_USE_OF_ENTERPRISE_AI, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        if (bundle.userAnalysis.isEmpty()) {
            r.setStatus(InsightResult.Status.NO_DATA.name());
            r.setHeadline("No agent usage data available");
            return r;
        }

        long totalInteractions = 0, offDomainInteractions = 0;
        int agentsWithDescription = 0, agentsWithoutDescription = 0;
        Map<String, Long> offDomainByUser = new HashMap<>();

        for (UserAnalysisData d : bundle.userAnalysis) {
            if (d.getTopicHierarchy() == null || d.getTopicHierarchy().isEmpty()) continue;
            String agent = d.getId() != null ? d.getId().getServiceId() : null;
            long rowTotal = 0;
            for (Map<String, Integer> subDomains : d.getTopicHierarchy().values()) {
                for (int c : subDomains.values()) rowTotal += c;
            }
            totalInteractions += rowTotal;

            if (scope != Scope.DETAIL) continue; // classification deferred to detail
            String description = InsightUtil.agentDescription(bundle, agent);
            if (description == null) { agentsWithoutDescription++; continue; }
            agentsWithDescription++;

            List<String> domains = new ArrayList<>(d.getTopicHierarchy().keySet());
            Map<String, String> classification = InsightClassificationHelper.classifyDomains(description, domains);
            long rowOffDomain = 0;
            for (Map.Entry<String, Map<String, Integer>> e : d.getTopicHierarchy().entrySet()) {
                if (!"ON".equals(classification.get(e.getKey()))) {
                    for (int c : e.getValue().values()) rowOffDomain += c;
                }
            }
            offDomainInteractions += rowOffDomain;
            if (d.getUserName() != null && rowOffDomain > 0) offDomainByUser.merge(d.getUserName(), rowOffDomain, Long::sum);
        }

        r.addMetric(new InsightResult.Metric("totalInteractions", "Total interactions", totalInteractions, "count", InsightUtil.count(totalInteractions, "interactions")));

        if (scope != Scope.DETAIL) {
            r.setMetricsComplete(false);
            r.setStatus(InsightResult.Status.PARTIAL.name());
            r.addDataGap(new InsightResult.Gap("AGENT_DESCRIPTION", "DEFERRED_TO_DETAIL", "Off-domain classification runs only when this insight is opened."));
            r.setHeadline(InsightUtil.count(totalInteractions, "interactions") + " observed; off-domain share computed on open");
            r.addCta(new InsightResult.Cta("view_llm", "View LLM observability", "NAVIGATE", InsightRoutes.LLM_OBSERVABILITY, new HashMap<>(), true));
            return r;
        }

        double offDomainShare = totalInteractions > 0 ? (double) offDomainInteractions / totalInteractions : 0;
        r.addMetric(new InsightResult.Metric("offDomainInteractions", "Off-domain interactions", offDomainInteractions, "count",
                InsightUtil.count(offDomainInteractions, "interactions")));
        r.addMetric(new InsightResult.Metric("offDomainShare", "Off-domain share", offDomainShare, "percent", InsightUtil.percent(offDomainShare)));

        r.setStatus((agentsWithDescription == 0 ? InsightResult.Status.PARTIAL : InsightResult.Status.READY).name());
        if (agentsWithoutDescription > 0) {
            r.addDataGap(new InsightResult.Gap("AGENT_DESCRIPTION", "NO_ROWS",
                    agentsWithoutDescription + " agents have no description set, so off-domain use could not be judged for them."));
        }
        r.setHeadline(agentsWithDescription == 0 ? "No agent has a description set — off-domain use cannot be judged"
                : InsightUtil.percent(offDomainShare) + " of interactions are off-domain for their agent");

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map.Entry<String, Long>> ranked = new ArrayList<>(offDomainByUser.entrySet());
        ranked.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        for (Map.Entry<String, Long> e : ranked.subList(0, Math.min(10, ranked.size()))) {
            Map<String, Object> row = new HashMap<>();
            row.put("user", e.getKey());
            row.put("offDomainInteractions", e.getValue());
            rows.add(row);
        }
        r.addEvidence(new InsightResult.Evidence("top_off_domain_users", "Top users by off-domain interactions",
                Arrays.asList("user", "offDomainInteractions"), rows, ranked.size()));

        r.addCta(new InsightResult.Cta("view_llm", "View LLM observability", "NAVIGATE", InsightRoutes.LLM_OBSERVABILITY, new HashMap<>(), true));
        r.addCta(new InsightResult.Cta("view_users", "View users and devices", "NAVIGATE", InsightRoutes.USERS_AND_DEVICES, new HashMap<>(), false));
        return r;
    }
}
