package com.akto.action.threat_detection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import com.akto.dto.type.URLMethods;

public class TestOwnAgentThreatStats {

    private static final int DAY = 86400;
    private static final int DAY1 = 100 * DAY;

    private static DashboardMaliciousEvent event(String id, String actor, String url, String host, String severity,
                                                 String category, String subCategory, long ts, String status, boolean exploit) {
        return new DashboardMaliciousEvent(id, actor, "filter-" + subCategory, url, URLMethods.Method.POST, 1, actor,
            "IN", "US", ts, "SINGLE", "ref-" + id, category, subCategory, "EVENT_TYPE_SINGLE", "", "",
            exploit, status, "THREAT", host, "", severity, "session-" + id);
    }

    private OwnAgentThreatStats stats() {
        return new OwnAgentThreatStats(Arrays.asList(
            event("1", "a1", "/chat", "bot.example.com", "CRITICAL", "PII", "PII-EMAIL", DAY1 + 10, "ACTIVE", true),
            event("2", "a1", "/chat", "bot.example.com", "HIGH", "PII", "PII-EMAIL", DAY1 + 20, "ACTIVE", false),
            event("3", "a2", "/skills/web-search", "bot.example.com", "LOW", "INJ", "PromptInjection", DAY1 + DAY + 5, "IGNORED", false),
            event("4", "a3", "/tools", "agent.example.com", "MEDIUM", "INJ", "PromptInjection", DAY1 + DAY + 50, "UNDER_REVIEW", false)
        ));
    }

    @Test
    public void testSeverityCounts() {
        List<ThreatCategoryCount> counts = stats().severityCounts();
        assertEquals(4, counts.size());
        assertEquals("CRITICAL", counts.get(0).getSubCategory());
        assertEquals(1, counts.get(0).getCount());
    }

    @Test
    public void testSubCategoryCounts() {
        HashMap<String, String> names = new HashMap<>();
        names.put("PII", "Sensitive data");
        List<ThreatCategoryCount> counts = stats().subCategoryCounts(names);
        assertEquals(2, counts.size());
        assertEquals(2, counts.get(0).getCount());
        assertTrue(counts.stream().anyMatch(c -> "Sensitive data".equals(c.getCategory()) && "PII-EMAIL".equals(c.getSubCategory())));
    }

    @Test
    public void testDailyActorsAndSummary() {
        OwnAgentThreatStats stats = stats();
        List<DailyActorsCount> days = stats.dailyActors();
        assertEquals(2, days.size());
        assertEquals(DAY1, days.get(0).getTs());
        assertEquals(1, days.get(0).getTotalActors());     // a1 twice on day 1
        assertEquals(1, days.get(0).getCriticalActors());  // a1's worst that day is CRITICAL
        assertEquals(2, days.get(1).getTotalActors());     // a2 and a3 on day 2
        assertEquals(0, days.get(1).getCriticalActors());
        assertEquals(1, stats.totalCriticalActors(days));
        assertEquals(1, stats.successfulExploits());
        assertEquals(2, stats.countByStatus("ACTIVE"));
        assertEquals(1, stats.countByStatus("IGNORED"));
        assertEquals(1, stats.countByStatus("UNDER_REVIEW"));
        assertEquals(1, stats.activeActors());
    }

    @Test
    public void testTimelineAndTops() {
        OwnAgentThreatStats stats = stats();
        assertEquals(2, stats.activityTimeline().size());
        assertEquals("/chat", stats.topApis(8).get(0).getEndpoint());
        assertEquals(2, stats.topApis(8).get(0).getAttacks());
        assertEquals("CRITICAL", stats.topApis(8).get(0).getSeverity());
        assertEquals("bot.example.com", stats.topHosts(8).get(0).getHost());
        assertEquals(3, stats.topHosts(8).get(0).getAttacks());
        assertEquals(1, stats.topHosts(1).size());
        assertEquals("a1", stats.dashboardTopActors(5).get(0).getActor());
        assertEquals(2, stats.dashboardTopActors(5).get(0).getAttackCount());
        assertEquals(1, stats.countSince(DAY1 + DAY + 50));
    }

    @Test
    public void testSkillSeverityCounts() {
        List<SkillSeverityCount> skills = stats().skillSeverityCounts();
        assertEquals(1, skills.size());
        assertEquals("web-search", skills.get(0).getSkillName());
        assertEquals(1, skills.get(0).getLow());
    }

    @Test
    public void testThreatApis() {
        List<DashboardThreatApi> apis = stats().threatApis();
        assertEquals(3, apis.size());
        DashboardThreatApi chat = apis.stream().filter(a -> "/chat".equals(a.getApi())).findFirst().get();
        assertEquals(2, chat.getRequestsCount());
        assertEquals(1, chat.getActorsCount());
        assertEquals(DAY1 + 10, chat.getDiscoveredAt());
        // latest first
        assertEquals("/tools", apis.get(0).getApi());
    }

    @Test
    public void testActorsPerCountry() {
        List<ThreatActorPerCountry> countries = stats().actorsPerCountry();
        assertEquals(1, countries.size());
        assertEquals("IN", countries.get(0).getCountry());
        assertEquals(3, countries.get(0).getCount()); // a1, a2, a3 counted once each
    }

    @Test
    public void testNoEvents() {
        OwnAgentThreatStats stats = new OwnAgentThreatStats(Collections.emptyList());
        assertTrue(stats.severityCounts().isEmpty());
        assertTrue(stats.dailyActors().isEmpty());
        assertTrue(stats.threatApis().isEmpty());
        assertEquals(0, new OwnAgentThreatStats(null).successfulExploits());
    }
}
