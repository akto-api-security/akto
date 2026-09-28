package com.akto.service.insights.providers.agentic;

import com.akto.dto.ApiCollection;
import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.service.insights.AbstractInsightProvider;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightRoutes;
import com.akto.service.insights.InsightUtil;
import com.akto.service.insights.agentic.AgentRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-agent observability risk — token-burn outliers, from a live per-serviceId ES aggregation
 * (SearchClient#fetchAgenticServiceObservability), deliberately NOT split by user the way
 * ENDPOINT's own per-device breakdown is (one row per agent, not per {agent, user} — see the
 * CLAUDE.md decision this was built from). Joined back to an agent via
 * InsightDataBundle#collectionsForServiceName, the same serviceId -> collection(s) lookup every
 * other agentic-name resolution in this codebase already uses.
 *
 * Deliberately does NOT use UserAnalysisData#getHarmfulTopics — that's an LLM-classified, Mongo-
 * cron-only rollup with no live ES/ADX equivalent (see SearchClient#fetchAgenticServiceObservability's
 * own javadoc for why). getDominantTopics is real, live topic-hierarchy data instead, used here only
 * as context for why an outlier agent burned as many tokens as it did — not as a severity signal.
 */
public class AgentObservabilityRiskProvider extends AbstractInsightProvider {

    private static final int MAX_FINDINGS = 10;

    public AgentObservabilityRiskProvider() { super(InsightId.AGENT_OBSERVABILITY_RISK, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();
        if (!bundle.agentic.loaded) {
            r.addDataGap(new InsightResult.Gap("USER_ANALYSIS", "NOT_CONFIGURED",
                    "Observability data is unavailable outside the Argus (agentic) dashboard context."));
            r.setHeadline("Observability data is unavailable here.");
            return r;
        }

        List<UserAnalysisData> rows = bundle.agentic.serviceObservability;
        if (rows.isEmpty()) {
            r.setStatus(InsightResult.Status.NO_DATA.name());
            r.setHeadline("No agent activity analyzed in this window.");
            r.addDataGap(new InsightResult.Gap("USER_ANALYSIS", "NO_ROWS", "No agent query activity falls in this window yet."));
            return r;
        }

        List<Candidate> candidates = new ArrayList<>();
        for (UserAnalysisData row : rows) {
            if (row.getId() == null || row.getId().getServiceId() == null) continue;
            if (!isTokenBurnOutlier(row, rows)) continue;
            List<ApiCollection> matches = bundle.collectionsForServiceName(row.getId().getServiceId());
            AgentRef agent = matches.isEmpty() ? null : new AgentRef(matches.get(0));
            if (agent == null) continue;
            candidates.add(new Candidate(agent, row));
        }

        if (candidates.isEmpty()) {
            r.setStatus(InsightResult.Status.NO_DATA.name());
            r.setHeadline("No agents show outsized token-burn activity this window.");
            return r;
        }

        candidates.sort(Comparator.comparingLong(
                (Candidate c) -> -(c.row.getTotalInputTokens() + c.row.getTotalOutputTokens())));

        int emitted = 0;
        for (Candidate c : candidates) {
            if (emitted >= MAX_FINDINGS) break;
            long totalTokens = c.row.getTotalInputTokens() + c.row.getTotalOutputTokens();
            List<String> dominantTopics = c.row.getDominantTopics(1);
            String topic = dominantTopics.isEmpty() ? null : dominantTopics.get(0);

            InsightResult.Finding finding = new InsightResult.Finding();
            finding.setId(InsightId.AGENT_OBSERVABILITY_RISK.name() + "|" + c.agent.getCollectionId());
            finding.setSeverity("MEDIUM");
            finding.setAgentName(c.agent.getName());
            finding.setApiCollectionId(c.agent.getCollectionId());
            finding.setEnvironment(c.agent.getEnvironment());
            finding.setResource(topic != null ? topic : "token burn");
            finding.setTitle(c.agent.getName() + " burned " + InsightUtil.grouped(totalTokens) + " tokens, well above other agents");
            finding.setWhyItMatters(topic != null
                    ? c.agent.getName() + " consumed " + InsightUtil.grouped(totalTokens) + " tokens this window, mostly on " + topic + " activity."
                    : c.agent.getName() + " consumed " + InsightUtil.grouped(totalTokens) + " tokens this window — verify that usage is expected.");
            finding.setRemediation("Check " + c.agent.getName() + "'s usage pattern for a runaway loop or unexpected workload.");
            Map<String, Object> ctaParams = new HashMap<>();
            ctaParams.put("apiCollectionId", c.agent.getCollectionId());
            finding.setCta(new InsightResult.Cta("review_agent", "Open agent page", "NAVIGATE", InsightRoutes.LLM_OBSERVABILITY, ctaParams, true));
            r.addFinding(finding);
            emitted++;
        }

        r.setStatus(InsightResult.Status.READY.name());
        r.setSeverity("MEDIUM");
        r.setHeadline(InsightUtil.count(candidates.size(), "agent") + " with outsized token burn this window");
        r.addCta(new InsightResult.Cta("view_observability", "View LLM observability", "NAVIGATE", InsightRoutes.LLM_OBSERVABILITY, new HashMap<>(), false));
        return r;
    }

    /** Simple outlier rule: more than 3x the account's own average token burn this window — no
     *  external baseline exists to compare against, so the account's own agents are the baseline. */
    private boolean isTokenBurnOutlier(UserAnalysisData row, List<UserAnalysisData> all) {
        long total = row.getTotalInputTokens() + row.getTotalOutputTokens();
        if (total <= 0 || all.size() < 2) return false;
        long sum = 0;
        for (UserAnalysisData s : all) sum += s.getTotalInputTokens() + s.getTotalOutputTokens();
        double avg = sum / (double) all.size();
        return avg > 0 && total > avg * 3;
    }

    private static final class Candidate {
        final AgentRef agent;
        final UserAnalysisData row;
        Candidate(AgentRef agent, UserAnalysisData row) {
            this.agent = agent; this.row = row;
        }
    }
}
