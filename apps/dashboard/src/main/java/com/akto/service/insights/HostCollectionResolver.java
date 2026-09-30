package com.akto.service.insights;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dto.ApiCollection;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The exact/loose/claude-config three-tier host -> collection-id join, extracted from
 * AgenticObserveAction#attributeViolationCountsToCollections so any caller that needs to
 * attribute a raw host string (a threat-backend HostSeverityCount, a guardrail-activity
 * event) back to the ApiCollection(s) it belongs to shares one implementation instead of
 * a second hand-copied matcher quietly drifting from the original. Built once per bundle
 * load from the already-loaded {id, hostName} projection — no extra Mongo read.
 */
public class HostCollectionResolver {

    private final Map<String, List<Integer>> hostToIds = new HashMap<>();
    private final Map<String, List<Integer>> looseToIds = new HashMap<>();
    private final Map<String, List<Integer>> claudeDeviceToIds = new HashMap<>();
    private final List<Integer> allClaudeIds = new ArrayList<>();

    private static final String AGENTIC_SUFFIX = "-agentic";

    // Mirrors guardrails-service canonicalServerToken: lowercase, "_" and "-" treated as the same character.
    static String canonicalHost(String host) {
        return host.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public HostCollectionResolver(List<ApiCollection> collections) {
        if (collections == null) return;
        for (ApiCollection c : collections) {
            String hostName = c.getHostName();
            if (StringUtils.isBlank(hostName)) continue;

            hostToIds.computeIfAbsent(canonicalHost(hostName), k -> new ArrayList<>()).add(c.getId());

            String lk = deviceServiceKey(hostName);
            if (lk != null) {
                looseToIds.computeIfAbsent(lk, k -> new ArrayList<>()).add(c.getId());
            }

            String[] parts = hostName.split("\\.");
            String deviceId = parts.length > 0 ? parts[0] : null;
            String service = parts.length > 0 ? parts[parts.length - 1].toLowerCase(Locale.ROOT) : null;
            if (StringUtils.isNotBlank(deviceId) && "claude".equals(service)) {
                claudeDeviceToIds.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(c.getId());
                allClaudeIds.add(c.getId());
            }
        }
    }

    /** Empty (never null) when the host matches nothing — same "log/return empty" convention as
     *  the rest of the insights read layer. First exact hostName match (canonicalized), else the loose
     *  device+last-segment key, else (only for a 2-segment "<device>.claude"-shaped host) the
     *  claude-config fallback pool. */
    public List<Integer> resolve(String host) {
        if (StringUtils.isBlank(host)) return Collections.emptyList();

        // Same identity guardrails matches policies on: the Host header, case- and "_"/"-"-insensitive. MCP traffic on a
        // host that already has a non-agentic collection lands in "<host>-agentic" (HttpCallParser), so prefer that twin.
        List<Integer> ids = hostToIds.get(canonicalHost(host + AGENTIC_SUFFIX));
        if (ids == null || ids.isEmpty()) {
            ids = hostToIds.get(canonicalHost(host));
        }
        if (ids == null || ids.isEmpty()) {
            ids = looseToIds.get(deviceServiceKey(host));
        }
        if ((ids == null || ids.isEmpty()) && isClaudeConfigHost(host)) {
            String deviceId = host.split("\\.")[0];
            List<Integer> pool = claudeDeviceToIds.get(deviceId);
            if (pool == null || pool.isEmpty()) pool = allClaudeIds;
            ids = pool.isEmpty() ? null : Collections.singletonList(pool.get(0));
        }
        return ids == null ? Collections.emptyList() : ids;
    }

    // Host first (see resolve); when the host matches no collection (e.g. a model-provider endpoint), fall back to the
    // actor as an exact hostName match (e.g. a Bedrock IAM role that is the agent's hostName).
    public List<Integer> resolveEvent(String host, String actor) {
        List<Integer> ids = resolve(host);
        if (!ids.isEmpty() || StringUtils.isBlank(actor)) return ids;
        List<Integer> byActor = hostToIds.get(canonicalHost(actor));
        return byActor == null ? Collections.emptyList() : byActor;
    }

    // collectionId -> {severity -> count} over the events that resolve to a collection.
    public Map<Integer, Map<String, Integer>> severityByCollection(List<DashboardMaliciousEvent> hostCounts) {
        Map<Integer, Map<String, Integer>> out = new HashMap<>();
        if (hostCounts == null) return out;
        for (DashboardMaliciousEvent e : hostCounts) {
            if (e == null) continue;
            List<Integer> ids = resolveEvent(e.getHost(), e.getActor());
            if (ids.isEmpty()) continue;
            String severity = e.getSeverity() == null ? "UNKNOWN" : e.getSeverity().toUpperCase(Locale.ROOT);
            out.computeIfAbsent(ids.get(0), k -> new HashMap<>()).merge(severity, 1, Integer::sum);
        }
        return out;
    }

    /** Mirrors agenticObserveApi.js's deviceServiceKey exactly — device+service loose-match key
     *  for 2-segment vs 3-segment host attribution. */
    public static String deviceServiceKey(String hostName) {
        if (StringUtils.isBlank(hostName)) return null;
        String[] parts = hostName.split("\\.");
        if (parts.length < 2) return null;
        return parts[0] + " " + parts[parts.length - 1];
    }

    /** Mirrors agenticObserveApi.js's isClaudeConfigHost exactly. */
    public static boolean isClaudeConfigHost(String hostName) {
        if (StringUtils.isBlank(hostName)) return false;
        String[] parts = hostName.split("\\.");
        if (parts.length != 2) return false;
        String service = parts[1].toLowerCase(Locale.ROOT);
        return "claude-settings".equals(service) || "claude".equals(service);
    }
}
