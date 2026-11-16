package com.akto.service.insights.providers;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.service.atlas.AtlasToolRow;
import com.akto.service.guardrails.AtlasGuardrailUtils;
import com.akto.service.insights.AbstractInsightProvider;
import com.akto.service.insights.HostCollectionResolver;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightRoutes;
import com.akto.service.insights.InsightUtil;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.ThreatDetectionConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ViolationsPilingUpProvider extends AbstractInsightProvider {

    private static final int EVIDENCE_ROWS = 10;
    private static final int MIN_OPEN_VIOLATIONS = 5;

    public ViolationsPilingUpProvider() { super(InsightId.VIOLATIONS_PILING_UP, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        List<DashboardMaliciousEvent> events = AtlasGuardrailUtils.currentPeriodEvents(bundle);
        if (events == null) return failed("THREAT_BACKEND", "Could not load violations");
        if (bundle.policies == null) return failed("POLICY_STORE", "Could not load guardrail policies");
        Map<String, GuardrailPolicies> policiesByName = bundle.policies.stream().filter(p -> p.getName() != null)
                .collect(Collectors.toMap(GuardrailPolicies::getName, p -> p, (first, second) -> first));

        Map<Integer, ApiCollection> collectionsById = new HashMap<>();
        for (ApiCollection c : bundle.collections) collectionsById.put(c.getId(), c);
        HostCollectionResolver resolver = bundle.hostResolver();
        Map<String, PolicyOnAsset> groups = new HashMap<>();
        for (DashboardMaliciousEvent e : events) {
            if (e == null || !AtlasGuardrailUtils.isOpen(e) || StringUtils.isBlank(e.getFilterId()) || !policiesByName.containsKey(e.getFilterId())
                    || AtlasGuardrailUtils.isConfigFinding(e) || AtlasGuardrailUtils.isSkillFinding(e)) continue;
            List<Integer> ids = resolver.resolveEvent(e.getHost(), e.getActor());
            ApiCollection c = ids.isEmpty() ? null : collectionsById.get(ids.get(0));
            if (c == null) continue;
            String type = AgenticObserveUtil.getTypeFromCollection(c);
            if (!AtlasGuardrailUtils.AGENTIC_ASSET_TYPES.contains(type)) continue;
            AtlasToolRow asset = AtlasGuardrailUtils.collectionAsset(c, type);
            String key = AtlasGuardrailUtils.assetKey(c, type, asset);
            groups.computeIfAbsent(e.getFilterId() + "|" + key, k -> new PolicyOnAsset(e.getFilterId(), key,
                    asset == null ? c.getHostName() : asset.getName(), asset == null ? c.getHostName() : null)).add(e, bundle);
        }

        InsightResult r = skeleton();
        List<PolicyOnAsset> ranked = groups.values().stream().filter(g -> g.count >= MIN_OPEN_VIOLATIONS)
                .sorted(ViolationGroup.MOST_REACH).collect(Collectors.toList());
        if (ranked.isEmpty()) {
            r.setHeadline("No agent has " + MIN_OPEN_VIOLATIONS + " or more open violations of one policy in this period.");
            return r;
        }
        PolicyOnAsset worst = ranked.get(0);

        r.setStatus(InsightResult.Status.READY.name());
        r.setSeverity(worst.severity);
        r.setHeadline(worst.employees() + " · " + worst.name + " · " + worst.policy + " · " + worst.decision());
        worst.addMetrics(r);
        r.addEvidence(new InsightResult.Evidence("groups", "Policies piling up on an agent",
                Arrays.asList("policy", "name", "violations", "employees", "devices", "severity", "decision"),
                ranked.stream().limit(EVIDENCE_ROWS).map(PolicyOnAsset::evidenceRow).collect(Collectors.toList()), ranked.size()));

        Map<String, Object> review = new HashMap<>();
        review.put("latestAttack", worst.policy);
        review.put("eventStatus", ThreatDetectionConstants.ACTIVE);
        if (worst.host != null) {
            review.put("host", worst.host);
        } else {
            review.put("atlasTool", worst.key);
            review.put("atlasToolName", worst.name);
        }
        r.addCta(new InsightResult.Cta("review_violations", "Review", "NAVIGATE", InsightRoutes.THREAT_ACTIVITY, review, true));
        GuardrailPolicies policy = policiesByName.get(worst.policy);
        boolean canSwitchToBlock = policy.isActive() && !AtlasGuardrailUtils.BEHAVIOUR_BLOCK.equalsIgnoreCase(policy.getBehaviour());
        r.addCta(canSwitchToBlock
                ? new InsightResult.Cta("switch_to_block", "Switch to block", "NAVIGATE", InsightRoutes.GUARDRAIL_POLICIES, InsightUtil.guardrailPolicyParams(worst.policy), false)
                : new InsightResult.Cta("open_policy", "Open policy", "NAVIGATE", InsightRoutes.GUARDRAIL_POLICIES, InsightUtil.guardrailPolicyParams(worst.policy), false));
        return r;
    }

    private static class PolicyOnAsset extends ViolationGroup {
        private final String policy;
        private final String key;
        private final String host;

        private PolicyOnAsset(String policy, String key, String name, String host) {
            super(name);
            this.policy = policy;
            this.key = key;
            this.host = host;
        }

        @Override
        Map<String, Object> evidenceRow() {
            Map<String, Object> row = super.evidenceRow();
            row.put("policy", policy);
            return row;
        }
    }
}
