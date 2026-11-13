package com.akto.service.insights.providers;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dto.ApiCollection;
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
import com.akto.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ViolationSpikeProvider extends AbstractInsightProvider {

    private static final int SPIKE_MIN_RATIO = 2;
    private static final int SPIKE_MIN_INCREASE = 20;
    private static final int EVIDENCE_ROWS = 10;
    private static final String KIND_EMPLOYEE = "Employee";
    private static final String KIND_AGENT = "Agent";

    public ViolationSpikeProvider() { super(InsightId.VIOLATION_SPIKE, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        Pair<List<DashboardMaliciousEvent>, List<DashboardMaliciousEvent>> split = AtlasGuardrailUtils.splitByPeriod(bundle);
        if (split == null) return failed("THREAT_BACKEND", "Could not load violations");
        InsightResult r = skeleton();
        if (split.getSecond() == null) {
            r.setHeadline("There is no previous period to compare with.");
            return r;
        }

        Map<Integer, ApiCollection> collectionsById = new HashMap<>();
        for (ApiCollection c : bundle.collections) collectionsById.put(c.getId(), c);
        Map<String, Spike> employees = new HashMap<>();
        Map<String, Spike> agents = new HashMap<>();
        countPeriod(split.getFirst(), true, bundle, collectionsById, employees, agents);
        countPeriod(split.getSecond(), false, bundle, collectionsById, employees, agents);

        List<Spike> spikes = new ArrayList<>(employees.values());
        spikes.addAll(agents.values());
        List<Spike> ranked = spikes.stream().filter(Spike::isSpike)
                .sorted(Comparator.comparingLong(Spike::increase).reversed()).collect(Collectors.toList());
        if (ranked.isEmpty()) {
            r.setHeadline("No sudden rise in violations compared with the previous period.");
            return r;
        }
        Spike worst = ranked.get(0);
        Long days = AtlasGuardrailUtils.periodDays(bundle);

        r.setStatus(InsightResult.Status.READY.name());
        r.setSeverity(worst.severity);
        String period = InsightUtil.count(days == null ? 0 : days, "days");
        r.setTitle("Violation spike on " + worst.name);
        String rise = worst.previous == 0 ? " · up from 0"
                : " · up " + InsightUtil.grouped(Math.round(100.0 * worst.increase() / worst.previous)) + "% from " + InsightUtil.grouped(worst.previous);
        r.setHeadline(InsightUtil.count(worst.count, "violations") + rise + " in the previous " + period);
        worst.addMetrics(r);
        r.addEvidence(new InsightResult.Evidence("spikes", "Sudden rise in violations",
                Arrays.asList("name", "kind", "violations", "previous", "employees", "severity"),
                ranked.stream().limit(EVIDENCE_ROWS).map(Spike::evidenceRow).collect(Collectors.toList()), ranked.size()));

        Map<String, Object> review = new HashMap<>();
        if (KIND_EMPLOYEE.equals(worst.kind)) {
            review.put("host", String.join(",", worst.hosts));
        } else if (worst.host != null) {
            review.put("host", worst.host);
        } else {
            review.put("atlasTool", worst.key);
            review.put("atlasToolName", worst.name);
        }
        r.addCta(new InsightResult.Cta("review_violations", "Review", "NAVIGATE", InsightRoutes.THREAT_ACTIVITY, review, true));
        return r;
    }

    private static void countPeriod(List<DashboardMaliciousEvent> events, boolean current, InsightDataBundle bundle,
                                    Map<Integer, ApiCollection> collectionsById, Map<String, Spike> employees, Map<String, Spike> agents) {
        HostCollectionResolver resolver = bundle.hostResolver();
        for (DashboardMaliciousEvent e : events) {
            String employee = employeeKey(e, bundle);
            if (employee != null) {
                if (current) employees.computeIfAbsent(employee, k -> new Spike(k, KIND_EMPLOYEE, k, null)).add(e, bundle);
                else if (employees.containsKey(employee)) employees.get(employee).previous++;
            }
            List<Integer> ids = resolver.resolveEvent(e.getHost(), e.getActor());
            ApiCollection c = ids.isEmpty() ? null : collectionsById.get(ids.get(0));
            if (c == null) continue;
            String type = AgenticObserveUtil.getTypeFromCollection(c);
            if (!AtlasGuardrailUtils.AGENTIC_ASSET_TYPES.contains(type)) continue;
            AtlasToolRow asset = AtlasGuardrailUtils.collectionAsset(c, type);
            String key = AtlasGuardrailUtils.assetKey(c, type, asset);
            if (current) {
                agents.computeIfAbsent(key, k -> new Spike(asset == null ? c.getHostName() : asset.getName(), KIND_AGENT, k,
                        asset == null ? c.getHostName() : null)).add(e, bundle);
            } else if (agents.containsKey(key)) {
                agents.get(key).previous++;
            }
        }
    }

    private static String employeeKey(DashboardMaliciousEvent e, InsightDataBundle bundle) {
        String deviceId = AgenticObserveUtil.extractEndpointId(e.getHost());
        if (deviceId == null) return null;
        String username = bundle.usernameForDevice(deviceId);
        return username != null ? username : deviceId;
    }

    private static class Spike extends ViolationGroup {
        private final String kind;
        private final String key;
        private final String host;
        private long previous;

        private Spike(String name, String kind, String key, String host) {
            super(name);
            this.kind = kind;
            this.key = key;
            this.host = host;
        }

        private long increase() {
            return count - previous;
        }

        private boolean isSpike() {
            return count >= SPIKE_MIN_RATIO * previous && increase() >= SPIKE_MIN_INCREASE;
        }

        @Override
        Map<String, Object> evidenceRow() {
            Map<String, Object> row = super.evidenceRow();
            row.put("kind", kind);
            row.put("previous", previous);
            return row;
        }
    }
}
