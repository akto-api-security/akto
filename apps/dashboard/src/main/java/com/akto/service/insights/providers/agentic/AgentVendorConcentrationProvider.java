package com.akto.service.insights.providers.agentic;

import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.service.insights.AbstractInsightProvider;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightRoutes;
import com.akto.service.insights.InsightUtil;
import com.akto.service.insights.agentic.AgentIndex;
import com.akto.service.insights.agentic.AgentRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * READ_ONLY context card: which vendor (bedrock, kiro, openai, anthropic, ...) this account's
 * agentic footprint concentrates on — InsightUtil#agenticVendorOf groups by hostname/collection-
 * name/asset-tag substring match, the same canonicalVendorName map RiskScoreCalculator's vendor
 * table already uses. Guardrail-hit counts per vendor reuse the same hostSeverityCounts +
 * AgentIndex join AgentGuardrailHotspotsProvider does, grouped one level up (by vendor, not agent).
 */
public class AgentVendorConcentrationProvider extends AbstractInsightProvider {

    private static final int MAX_FINDINGS = 8;
    private static final String UNKNOWN_VENDOR = "Unattributed";

    public AgentVendorConcentrationProvider() { super(InsightId.AGENT_VENDOR_CONCENTRATION, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();
        if (!bundle.agentic.loaded) {
            r.addDataGap(new InsightResult.Gap("DEVICE_IDENTITY", "NOT_CONFIGURED",
                    "Vendor concentration is unavailable outside the Argus (agentic) dashboard context."));
            r.setHeadline("Vendor concentration is unavailable here.");
            return r;
        }

        AgentIndex agentIndex = bundle.agentic.agentIndex;
        Map<String, List<AgentRef>> agentsByVendor = new HashMap<>();
        for (AgentRef agent : agentIndex.all()) {
            if (agent.isDeactivated()) continue;
            String vendor = agent.getVendor() != null ? agent.getVendor() : UNKNOWN_VENDOR;
            agentsByVendor.computeIfAbsent(vendor, k -> new ArrayList<>()).add(agent);
        }

        if (agentsByVendor.isEmpty()) {
            r.setStatus(InsightResult.Status.NO_DATA.name());
            r.setHeadline("No agentic assets discovered yet.");
            return r;
        }

        Map<String, Integer> hitsByVendor = new HashMap<>();
        for (HostSeverityCount hsc : bundle.hostSeverityCounts) {
            AgentRef agent = agentIndex.agentForHost(hsc.getHost());
            if (agent == null) continue;
            String vendor = agent.getVendor() != null ? agent.getVendor() : UNKNOWN_VENDOR;
            hitsByVendor.merge(vendor, hsc.getCritical() + hsc.getHigh() + hsc.getMedium() + hsc.getLow(), Integer::sum);
        }

        int totalAgents = agentIndex.allCollectionIds().size();
        List<String> vendors = new ArrayList<>(agentsByVendor.keySet());
        vendors.sort(Comparator.comparingInt((String v) -> -agentsByVendor.get(v).size()));

        int emitted = 0;
        for (String vendor : vendors) {
            if (emitted >= MAX_FINDINGS) break;
            List<AgentRef> agents = agentsByVendor.get(vendor);
            double share = totalAgents > 0 ? (agents.size() * 100.0) / totalAgents : 0;
            int hits = hitsByVendor.getOrDefault(vendor, 0);

            InsightResult.Finding finding = new InsightResult.Finding();
            finding.setId(InsightId.AGENT_VENDOR_CONCENTRATION.name() + "|" + vendor);
            finding.setSeverity(null); // read-only, no actionable severity
            finding.setAgentName(vendor);
            finding.setApiCollectionId(agents.get(0).getCollectionId());
            finding.setEnvironment(null);
            finding.setResource(InsightUtil.count(agents.size(), "agent"));
            finding.setTitle(vendor + " accounts for " + InsightUtil.percent(share / 100.0) + " of agentic assets");
            finding.setWhyItMatters(hits > 0
                    ? vendor + "-backed agents also generated " + InsightUtil.count(hits, "guardrail hit") + " this window."
                    : vendor + " is a concentration point — a compromise there affects every agent on it.");
            r.addFinding(finding);
            r.addMetric(new InsightResult.Metric("agents_" + vendor, vendor + " — agents", agents.size(), "count", String.valueOf(agents.size())));
            emitted++;
        }

        r.setStatus(InsightResult.Status.READY.name());
        r.setHeadline(InsightUtil.count(vendors.size(), "vendor") + " across " + InsightUtil.count(totalAgents, "agent"));
        r.addCta(new InsightResult.Cta("view_assets", "View agentic assets", "NAVIGATE", InsightRoutes.AGENTIC_ASSETS, new HashMap<>(), false));
        return r;
    }
}
