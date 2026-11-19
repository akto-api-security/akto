package com.akto.action.threat_detection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/*
 * Guardrail activity totals and charts for users limited to specific collections (Argus), computed
 * from their own events. The threat backend aggregates over the whole account and cannot be
 * narrowed by host, so for these users the same numbers are built here from the host-scoped
 * event list instead. Mirrors the threat backend's aggregations as closely as the event fields allow.
 */
public class OwnAgentThreatStats {

    private static final List<String> SEVERITIES = Arrays.asList("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final int DAY_SECONDS = 86400;

    private final List<DashboardMaliciousEvent> events;

    public OwnAgentThreatStats(List<DashboardMaliciousEvent> events) {
        this.events = events == null ? new ArrayList<>() : events;
    }

    public List<DashboardMaliciousEvent> getEvents() {
        return events;
    }

    public List<ThreatCategoryCount> severityCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (DashboardMaliciousEvent e : events) {
            if (e.getSeverity() != null && SEVERITIES.contains(e.getSeverity())) {
                counts.merge(e.getSeverity(), 1, Integer::sum);
            }
        }
        List<ThreatCategoryCount> result = new ArrayList<>();
        for (String severity : SEVERITIES) {
            Integer count = counts.get(severity);
            if (count != null && count > 0) {
                result.add(new ThreatCategoryCount("", severity, count));
            }
        }
        return result;
    }

    public List<ThreatCategoryCount> subCategoryCounts(Map<String, String> categoryDisplayNames) {
        Map<List<String>, Integer> counts = new HashMap<>();
        for (DashboardMaliciousEvent e : events) {
            counts.merge(Arrays.asList(e.getCategory(), e.getSubCategory()), 1, Integer::sum);
        }
        List<ThreatCategoryCount> result = new ArrayList<>();
        counts.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(entry -> {
                String category = entry.getKey().get(0);
                result.add(new ThreatCategoryCount(
                    categoryDisplayNames.getOrDefault(category, category), entry.getKey().get(1), entry.getValue()));
            });
        return result;
    }

    /* Per day: distinct actors, and actors whose most severe event that day is CRITICAL */
    public List<DailyActorsCount> dailyActors() {
        Map<Integer, Map<String, Integer>> dayToActorSeverity = new TreeMap<>();
        for (DashboardMaliciousEvent e : events) {
            int day = dayStart(e.getTimestamp());
            int priority = severityPriority(e.getSeverity());
            dayToActorSeverity.computeIfAbsent(day, d -> new HashMap<>()).merge(String.valueOf(e.getActor()), priority, Math::max);
        }
        List<DailyActorsCount> result = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Integer>> day : dayToActorSeverity.entrySet()) {
            int critical = 0;
            for (int priority : day.getValue().values()) {
                if (priority == severityPriority("CRITICAL")) critical++;
            }
            result.add(new DailyActorsCount(day.getKey(), day.getValue().size(), critical));
        }
        return result;
    }

    public int totalCriticalActors(List<DailyActorsCount> dailyActors) {
        int total = 0;
        for (DailyActorsCount day : dailyActors) {
            total += day.getCriticalActors();
        }
        return total;
    }

    public int successfulExploits() {
        int total = 0;
        for (DashboardMaliciousEvent e : events) {
            if (e.getSuccessfulExploit()) total++;
        }
        return total;
    }

    public int countByStatus(String status) {
        int total = 0;
        for (DashboardMaliciousEvent e : events) {
            if (status.equalsIgnoreCase(e.getStatus())) total++;
        }
        return total;
    }

    public int activeActors() {
        Set<String> actors = new HashSet<>();
        for (DashboardMaliciousEvent e : events) {
            if ("ACTIVE".equalsIgnoreCase(e.getStatus())) actors.add(String.valueOf(e.getActor()));
        }
        return actors.size();
    }

    /* Per day: event count per sub category */
    public List<ThreatActivityTimeline> activityTimeline() {
        Map<Integer, Map<String, Integer>> dayToSubCategory = new TreeMap<>();
        for (DashboardMaliciousEvent e : events) {
            dayToSubCategory.computeIfAbsent(dayStart(e.getTimestamp()), d -> new LinkedHashMap<>())
                .merge(String.valueOf(e.getSubCategory()), 1, Integer::sum);
        }
        List<ThreatActivityTimeline> result = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Integer>> day : dayToSubCategory.entrySet()) {
            List<SubCategoryWiseData> data = new ArrayList<>();
            for (Map.Entry<String, Integer> sub : day.getValue().entrySet()) {
                data.add(new SubCategoryWiseData(sub.getKey(), sub.getValue()));
            }
            result.add(new ThreatActivityTimeline(day.getKey(), data));
        }
        return result;
    }

    public List<TopApiData> topApis(int limit) {
        Map<List<String>, int[]> apis = new HashMap<>(); // [attacks, max severity priority]
        for (DashboardMaliciousEvent e : events) {
            int[] stats = apis.computeIfAbsent(Arrays.asList(e.getUrl(), methodName(e)), k -> new int[2]);
            stats[0]++;
            stats[1] = Math.max(stats[1], severityPriority(e.getSeverity()));
        }
        List<TopApiData> result = new ArrayList<>();
        apis.entrySet().stream()
            .sorted((a, b) -> b.getValue()[0] - a.getValue()[0])
            .limit(limit)
            .forEach(entry -> result.add(new TopApiData(entry.getKey().get(0), entry.getKey().get(1),
                entry.getValue()[0], severityName(entry.getValue()[1]))));
        return result;
    }

    public List<TopHostData> topHosts(int limit) {
        Map<String, Integer> hosts = new HashMap<>();
        for (DashboardMaliciousEvent e : events) {
            if (e.getHost() != null) hosts.merge(e.getHost(), 1, Integer::sum);
        }
        List<TopHostData> result = new ArrayList<>();
        hosts.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .limit(limit)
            .forEach(entry -> result.add(new TopHostData(entry.getKey(), entry.getValue())));
        return result;
    }

    public List<DashboardTopActorData> dashboardTopActors(int limit) {
        Map<String, List<DashboardMaliciousEvent>> byActor = new HashMap<>();
        for (DashboardMaliciousEvent e : events) {
            byActor.computeIfAbsent(String.valueOf(e.getActor()), a -> new ArrayList<>()).add(e);
        }
        List<DashboardTopActorData> result = new ArrayList<>();
        byActor.entrySet().stream()
            .sorted((a, b) -> b.getValue().size() - a.getValue().size())
            .limit(limit)
            .forEach(entry -> {
                DashboardMaliciousEvent latest = entry.getValue().stream()
                    .max(Comparator.comparingLong(DashboardMaliciousEvent::getTimestamp)).orElse(null);
                result.add(new DashboardTopActorData(entry.getKey(), entry.getValue().size(),
                    latest != null ? latest.getCountry() : null, latest != null ? latest.getFilterId() : null));
            });
        return result;
    }

    public List<DashboardTopApiData> dashboardTopApis(int limit) {
        Map<List<String>, Set<String>> apiActors = new HashMap<>();
        Map<List<String>, Integer> apiRequests = new HashMap<>();
        for (DashboardMaliciousEvent e : events) {
            List<String> key = Arrays.asList(e.getUrl(), methodName(e), e.getHost());
            apiRequests.merge(key, 1, Integer::sum);
            apiActors.computeIfAbsent(key, k -> new HashSet<>()).add(String.valueOf(e.getActor()));
        }
        List<DashboardTopApiData> result = new ArrayList<>();
        apiRequests.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .limit(limit)
            .forEach(entry -> result.add(new DashboardTopApiData(entry.getKey().get(0), entry.getKey().get(1),
                entry.getKey().get(2), entry.getValue(), apiActors.get(entry.getKey()).size())));
        return result;
    }

    /* Skill invocations (/skills/<name> endpoints) per skill and severity, same as the backend */
    public List<SkillSeverityCount> skillSeverityCounts() {
        String prefix = "/skills/";
        Map<String, int[]> bySkill = new LinkedHashMap<>(); // [critical, high, medium, low]
        for (DashboardMaliciousEvent e : events) {
            String url = e.getUrl();
            if (url == null || !url.startsWith(prefix) || url.length() == prefix.length()) continue;
            int index = e.getSeverity() == null ? -1 : SEVERITIES.indexOf(e.getSeverity().toUpperCase());
            int[] counts = bySkill.computeIfAbsent(url.substring(prefix.length()), k -> new int[4]);
            if (index >= 0) counts[index]++;
        }
        List<SkillSeverityCount> result = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : bySkill.entrySet()) {
            int[] c = entry.getValue();
            result.add(new SkillSeverityCount(entry.getKey(), c[0], c[1], c[2], c[3]));
        }
        return result;
    }

    /* Per API (endpoint, method, host): distinct actors, requests and first detection - latest first */
    public List<DashboardThreatApi> threatApis() {
        Map<List<String>, DashboardThreatApi> apis = new LinkedHashMap<>();
        Map<List<String>, Set<String>> apiActors = new HashMap<>();
        for (DashboardMaliciousEvent e : events) {
            List<String> key = Arrays.asList(e.getUrl(), methodName(e), e.getHost());
            DashboardThreatApi api = apis.computeIfAbsent(key,
                k -> new DashboardThreatApi(e.getUrl(), e.getMethod(), 0, 0, e.getTimestamp(), e.getHost()));
            api.setRequestsCount(api.getRequestsCount() + 1);
            api.setDiscoveredAt(Math.min(api.getDiscoveredAt(), e.getTimestamp()));
            apiActors.computeIfAbsent(key, k -> new HashSet<>()).add(String.valueOf(e.getActor()));
            api.setActorsCount(apiActors.get(key).size());
        }
        List<DashboardThreatApi> result = new ArrayList<>(apis.values());
        result.sort((a, b) -> Long.compare(b.getDiscoveredAt(), a.getDiscoveredAt()));
        return result;
    }

    /* Distinct actors per country */
    public List<ThreatActorPerCountry> actorsPerCountry() {
        Map<String, Set<String>> byCountry = new HashMap<>();
        for (DashboardMaliciousEvent e : events) {
            if (e.getCountry() == null || e.getCountry().isEmpty()) continue;
            byCountry.computeIfAbsent(e.getCountry(), c -> new HashSet<>()).add(String.valueOf(e.getActor()));
        }
        List<ThreatActorPerCountry> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : byCountry.entrySet()) {
            result.add(new ThreatActorPerCountry(entry.getKey(), entry.getValue().size()));
        }
        return result;
    }

    public int countSince(long sinceTs) {
        int total = 0;
        for (DashboardMaliciousEvent e : events) {
            if (e.getTimestamp() >= sinceTs) total++;
        }
        return total;
    }

    private static int dayStart(long ts) {
        return (int) (ts - (ts % DAY_SECONDS));
    }

    private static String methodName(DashboardMaliciousEvent e) {
        return e.getMethod() != null ? e.getMethod().name() : null;
    }

    private static int severityPriority(String severity) {
        if (severity == null) return 0;
        int index = SEVERITIES.indexOf(severity.toUpperCase());
        return index < 0 ? 0 : SEVERITIES.size() - index;
    }

    private static String severityName(int priority) {
        return priority <= 0 ? "UNKNOWN" : SEVERITIES.get(SEVERITIES.size() - priority);
    }
}
