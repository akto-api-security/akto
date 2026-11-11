package com.akto.service.atlas;

import com.akto.dto.McpAuditInfo;
import com.akto.util.Constants;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestAtlasDiscoveryService {

    private static final int NOW = 1_800_000_000;
    private static final int DAY = 24 * 60 * 60;

    private static AtlasToolRow tool(String rowType, String key, double risk, int firstSeen, String... devices) {
        AtlasToolRow row = new AtlasToolRow();
        row.setRowType(rowType);
        row.setGroupKey(key);
        row.setName(key);
        row.setClientType("AI Agent");
        row.setRiskScore(risk);
        row.setFirstSeen(firstSeen);
        row.setLastSeen(NOW);
        row.setDeviceIds(new HashSet<>(Arrays.asList(devices)));
        return row;
    }

    private static Map<String, String> users(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    private static AtlasDiscoveryService service(List<AtlasToolRow> rows, Map<String, AtlasAssetStatus> decisions, Set<String> allowlist) {
        return new AtlasDiscoveryService(rows, decisions, allowlist, new AtlasPeople(users("d1", "user.one", "d2", "user.two"),
                users("user.one", "Platform")), NOW);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> overview, String name) {
        return (Map<String, Object>) overview.get(name);
    }

    @Test
    public void statusResolutionOrder() {
        AtlasAssetStatusService statuses = new AtlasAssetStatusService();
        AtlasToolRow agent = tool("agent", "sample-agent", 1, NOW, "d1");
        agent.setVendorName("Sample");
        Set<String> allowlist = new HashSet<>(Collections.singletonList("sample"));
        Map<String, AtlasAssetStatus> decisions = new HashMap<>();

        assertEquals(AtlasAssetStatus.APPROVED, statuses.resolve(agent, decisions, allowlist));
        assertEquals(AtlasAssetStatus.UNAPPROVED, statuses.resolve(agent, decisions, Collections.emptySet()));

        decisions.put("ai-agent|sample-agent", AtlasAssetStatus.UNDER_REVIEW);
        // A hand-set review beats the allowlist; an untouched (unapproved) row does not.
        assertEquals(AtlasAssetStatus.UNDER_REVIEW, statuses.resolve(agent, decisions, allowlist));
        assertEquals(AtlasAssetStatus.UNDER_REVIEW, statuses.resolve(agent, decisions, Collections.emptySet()));
        decisions.put("ai-agent|sample-agent", AtlasAssetStatus.UNAPPROVED);
        assertEquals(AtlasAssetStatus.APPROVED, statuses.resolve(agent, decisions, allowlist));

        decisions.put("ai-agent|sample-agent", AtlasAssetStatus.BLOCKED);
        assertEquals(AtlasAssetStatus.BLOCKED, statuses.resolve(agent, decisions, allowlist));
    }

    @Test
    public void flaggedToolIsNotApprovedByAllowlistedVendor() {
        AtlasToolRow skill = tool("skill", "flagged-skill", 0, NOW, "d1");
        skill.setVendorName("Sample");
        skill.setMalicious(true);

        assertEquals(AtlasAssetStatus.UNAPPROVED, new AtlasAssetStatusService().resolve(skill, new HashMap<>(), new HashSet<>(Collections.singletonList("sample"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void decisionsAreRankedAndCapped() {
        List<AtlasToolRow> rows = new ArrayList<>();
        for (int i = 0; i < 7; i++) rows.add(tool("service", "server-" + i, i, NOW - DAY, "d1", "d2"));
        rows.add(tool("service", "server-old", 5, NOW - 30 * DAY, "d1"));
        Map<String, AtlasAssetStatus> decisions = new HashMap<>();
        decisions.put("mcp-server|server-0", AtlasAssetStatus.APPROVED);

        Map<String, Object> overview = service(rows, decisions, Collections.emptySet()).buildOverview();
        Map<String, Object> decisionSection = section(overview, "decisions");
        List<Map<String, Object>> top = (List<Map<String, Object>>) decisionSection.get("rows");

        assertEquals(7, decisionSection.get("total"));
        assertEquals(5, top.size());
        assertEquals("server-6", top.get(0).get("name"));
        assertEquals(true, top.get(0).get("isNew"));
        assertTrue(((String) top.get(0).get("why")).contains("2 devices"));
        assertTrue(((List<String>) overview.get("unavailableSources")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void countsEmployeesOnceAndIgnoresUntrackedUsers() {
        AtlasToolRow shared = tool("agent", "sample-agent", 1, NOW, "d1", "d2", "d3");
        Map<String, Object> overview = service(Collections.singletonList(shared), new HashMap<>(), Collections.emptySet()).buildOverview();
        List<Map<String, Object>> adoption = (List<Map<String, Object>>) overview.get("adoption");

        assertEquals(1, adoption.size());
        assertEquals(2, adoption.get(0).get("employees"));
        assertEquals(3, adoption.get(0).get("devices"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void statusCountsOnlyGovernableTools() {
        List<AtlasToolRow> rows = Arrays.asList(
                tool("agent", "agent-a", 1, NOW, "d1"),
                tool("llm", "app-a", 1, NOW, "d1"),
                tool("skill", "skill-a", 1, NOW, "d1"));
        Map<String, Object> counts = section(service(rows, new HashMap<>(), Collections.emptySet()).buildOverview(), "statusCounts");

        assertEquals(2, counts.get("total"));
        assertEquals(2, counts.get("UNAPPROVED"));
        assertEquals(0, counts.get("BLOCKED"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void trendIsTheLastTwelveWeeksSplitByCurrentStatus() {
        List<AtlasToolRow> rows = Arrays.asList(
                tool("agent", "agent-a", 1, NOW - 3 * DAY, "d1"),
                tool("agent", "agent-b", 1, NOW - 10 * DAY, "d1"),
                tool("agent", "agent-old", 1, NOW - 100 * DAY, "d1"));
        Map<String, AtlasAssetStatus> decisions = new HashMap<>();
        decisions.put("ai-agent|agent-b", AtlasAssetStatus.APPROVED);

        List<Map<String, Object>> weeks = (List<Map<String, Object>>) service(rows, decisions, Collections.emptySet())
                .buildOverview().get("newToolsTrend");

        assertEquals(12, weeks.size());
        assertEquals(NOW - 12 * 7 * DAY, weeks.get(0).get("periodStart"));
        // The last week holds the tool seen 3 days ago, the one before it the tool seen 10 days ago; older tools are out.
        assertEquals(1, weeks.get(11).get("UNAPPROVED"));
        assertEquals(1, weeks.get(10).get("APPROVED"));
        assertEquals(0, weeks.get(0).get("UNAPPROVED"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void adoptionCountsOnlyEmployeesActiveInTheLastThirtyDays() {
        AtlasToolRow agent = tool("agent", "agent-a", 1, NOW - 90 * DAY, "d1", "d2");
        agent.getDeviceLastSeen().put("d1", NOW - DAY);
        agent.getDeviceLastSeen().put("d2", NOW - 40 * DAY);
        AtlasToolRow idle = tool("agent", "agent-idle", 1, NOW - 90 * DAY, "d1", "d2");
        idle.getDeviceLastSeen().put("d1", NOW - 50 * DAY);
        idle.getDeviceLastSeen().put("d2", NOW - 60 * DAY);
        Map<String, Object> overview = service(Arrays.asList(agent, idle), new HashMap<>(), Collections.emptySet()).buildOverview();

        List<Map<String, Object>> adoption = (List<Map<String, Object>>) overview.get("adoption");
        assertEquals(1, adoption.size());
        assertEquals("agent-a", adoption.get(0).get("name"));
        assertEquals(1, adoption.get(0).get("employees"));
        assertEquals(2, adoption.get(0).get("devices"));

        // Only user.one is tagged (Platform) and used a tool recently, so the team's agents share is 1 of 1.
        List<Map<String, Object>> teams = (List<Map<String, Object>>) section(overview, "teamCategoryMatrix").get("rows");
        assertEquals(Long.valueOf(100L), ((List<Long>) teams.get(0).get("percents")).get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aToolWithNoKnownLastUseIsNotCountedAsAdopted() {
        AtlasToolRow unknown = tool("agent", "agent-unknown", 1, NOW - 90 * DAY, "d1");
        unknown.setLastSeen(0);
        Map<String, Object> overview = service(Collections.singletonList(unknown), new HashMap<>(), Collections.emptySet()).buildOverview();
        assertTrue(((List<Map<String, Object>>) overview.get("adoption")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aFailingSectionBlanksOnlyItsOwnCard() {
        AtlasToolRow agent = tool("agent", "agent-a", 1, NOW, "d1");
        // No team data at all breaks the sections that read teams, not the rest of the page.
        AtlasDiscoveryService broken = new AtlasDiscoveryService(Collections.singletonList(agent), new HashMap<>(), Collections.emptySet(),
                new AtlasPeople(users("d1", "user.one"), null), NOW);
        Map<String, Object> overview = broken.buildOverview();
        List<String> unavailable = (List<String>) overview.get("unavailableSources");
        assertTrue(unavailable.contains("teamCategoryMatrix"));
        assertFalse(unavailable.contains("tiles"));
        assertTrue(overview.containsKey("tiles") && overview.containsKey("decisions"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void onlyMaliciousSkillsNeedADecision() {
        AtlasToolRow clean = tool("skill", "clean-skill", 1, NOW, "d1");
        AtlasToolRow flagged = tool("skill", "flagged-skill", 1, NOW, "d1");
        flagged.setMalicious(true);
        AtlasToolRow decided = tool("skill", "decided-skill", 1, NOW, "d1");
        decided.setMalicious(true);
        Map<String, AtlasAssetStatus> decisions = new HashMap<>();
        decisions.put("AGENT_SKILL|decided-skill", AtlasAssetStatus.BLOCKED);
        Map<String, Object> overview = service(Arrays.asList(clean, flagged, decided), decisions, Collections.emptySet()).buildOverview();

        List<Map<String, Object>> pending = (List<Map<String, Object>>) section(overview, "decisions").get("rows");
        assertEquals(1, pending.size());
        assertEquals("flagged-skill", pending.get(0).get("name"));
        assertTrue(((String) pending.get(0).get("why")).contains("Flagged as malicious"));

        Map<String, Object> skills = ((List<Map<String, Object>>) section(overview, "tiles").get("categories")).stream()
                .filter(category -> "skills".equals(category.get("id"))).findFirst().get();
        assertEquals(3, skills.get("count"));
        assertEquals(2, skills.get("malicious"));
        // Skills stay out of the inventory status counts.
        assertEquals(0, section(overview, "statusCounts").get("total"));
        assertFalse(AtlasAssetStatusService.canDecide(clean));
        assertTrue(AtlasAssetStatusService.canDecide(flagged));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void decisionsListRiskFirstThenMaliciousThenSpread() {
        AtlasToolRow scoredSkill = tool("skill", "scored-skill", 3.5, NOW, "d1");
        scoredSkill.setMalicious(true);
        AtlasToolRow scoredAgent = tool("agent", "scored-agent", 3, NOW, "d1", "d2");
        AtlasToolRow unscoredSkill = tool("skill", "unscored-skill", 0, NOW, "d1");
        unscoredSkill.setMalicious(true);
        AtlasToolRow widespread = tool("agent", "widespread-agent", 0, NOW, "d1", "d2", "d3");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) service(Arrays.asList(widespread, unscoredSkill, scoredAgent, scoredSkill),
                new HashMap<>(), Collections.emptySet()).pageDecisions(0, 10).get("rows");

        assertEquals(Arrays.asList("scored-skill", "scored-agent", "unscored-skill", "widespread-agent"),
                rows.stream().map(row -> row.get("name")).collect(Collectors.toList()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void decisionsKeepOnlyRiskyNewOrMaliciousTools() {
        AtlasToolRow fresh = tool("agent", "fresh-agent", 0, NOW - DAY, "d1");
        AtlasToolRow riskyOld = tool("agent", "risky-old-agent", 2, NOW - 30 * DAY, "d1");
        AtlasToolRow flagged = tool("skill", "flagged-skill", 0, NOW - 30 * DAY, "d1");
        flagged.setMalicious(true);
        AtlasToolRow quietOld = tool("agent", "quiet-old-agent", 0, NOW - 30 * DAY, "d1", "d2");
        Map<String, Object> page = service(Arrays.asList(quietOld, flagged, riskyOld, fresh), new HashMap<>(), Collections.emptySet()).pageDecisions(0, 10);

        assertEquals(3, page.get("total"));
        assertEquals(Arrays.asList("risky-old-agent", "flagged-skill", "fresh-agent"),
                ((List<Map<String, Object>>) page.get("rows")).stream().map(row -> row.get("name")).collect(Collectors.toList()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void tilesFollowTheCategoryOrder() {
        List<AtlasToolRow> rows = Arrays.asList(tool("agent", "agent-a", 1, NOW, "d1"), tool("service", "server-a", 1, NOW, "d1"));
        List<Map<String, Object>> categories = (List<Map<String, Object>>) section(
                service(rows, new HashMap<>(), Collections.emptySet()).buildOverview(), "tiles").get("categories");

        assertEquals(Arrays.asList("agents", "ide", "mcp", "browser", "skills"),
                categories.stream().map(category -> category.get("id")).collect(Collectors.toList()));
        assertEquals(1, categories.get(0).get("count"));
        assertEquals(0, categories.get(1).get("count"));
        assertEquals(1, categories.get(2).get("count"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void teamMatrixUsesTaggedHeadcount() {
        AtlasToolRow agent = tool("agent", "agent-a", 1, NOW, "d1");
        Map<String, Object> matrix = section(service(Collections.singletonList(agent), new HashMap<>(), Collections.emptySet()).buildOverview(),
                "teamCategoryMatrix");
        List<Map<String, Object>> teams = (List<Map<String, Object>>) matrix.get("rows");

        assertEquals(1, teams.size());
        assertEquals("Platform", teams.get(0).get("name"));
        assertEquals(Long.valueOf(100L), ((List<Long>) teams.get(0).get("percents")).get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void toolListFiltersSortsAndPages() {
        List<AtlasToolRow> rows = new ArrayList<>();
        rows.add(tool("agent", "alpha-agent", 1, NOW, "d1", "d2"));
        rows.add(tool("agent", "beta-agent", 3, NOW, "d1"));
        rows.add(tool("service", "gamma-server", 2, NOW, "d1", "d2"));
        Map<String, AtlasAssetStatus> decisions = new HashMap<>();
        decisions.put("mcp-server|gamma-server", AtlasAssetStatus.BLOCKED);
        AtlasDiscoveryService service = service(rows, decisions, Collections.emptySet());

        AtlasDiscoveryService.Query query = new AtlasDiscoveryService.Query();
        query.setLimit(2);
        query.setSortKey("riskScore");
        Map<String, Object> page = service.pageTools(query);
        List<Map<String, Object>> tools = (List<Map<String, Object>>) page.get("tools");
        assertEquals(3, page.get("total"));
        assertEquals(2, tools.size());
        assertEquals("beta-agent", tools.get(0).get("name"));

        query.setSortOrder(1);
        query.setSkip(2);
        assertEquals("beta-agent", ((List<Map<String, Object>>) service.pageTools(query).get("tools")).get(0).get("name"));

        AtlasDiscoveryService.Query blocked = new AtlasDiscoveryService.Query();
        blocked.setLimit(10);
        blocked.setStatus("BLOCKED");
        assertEquals(1, service.pageTools(blocked).get("total"));
        // Tab counts ignore the status filter.
        Map<String, Integer> counts = (Map<String, Integer>) service.pageTools(blocked).get("categoryCounts");
        assertEquals(Integer.valueOf(3), counts.get("all"));
        assertEquals(Integer.valueOf(2), counts.get("agents"));
        assertEquals(Integer.valueOf(1), counts.get("mcp"));

        AtlasDiscoveryService.Query search = new AtlasDiscoveryService.Query();
        search.setLimit(10);
        search.setText("ALPHA");
        search.setCategory("agents");
        assertEquals(1, service.pageTools(search).get("total"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void maliciousToolsAreCountedAsRiskyConfig() {
        AtlasToolRow bad = tool("service", "server-bad", 1, NOW, "d1");
        bad.setMalicious(true);
        List<Map<String, Object>> configs = (List<Map<String, Object>>) service(Collections.singletonList(bad), new HashMap<>(), Collections.emptySet())
                .buildOverview().get("riskyConfigs");

        assertEquals("malicious", configs.get(0).get("id"));
        assertEquals(1, configs.get(0).get("count"));
    }

    @Test
    public void deviceScopeKeepsOnlyMatchingDevices() {
        Map<String, Map<String, String>> metadata = new HashMap<>();
        metadata.put("Desk-One", new HashMap<>());
        Map<String, String> browser = new HashMap<>();
        browser.put("browserName", "Chrome");
        metadata.put("Browser-One", browser);
        Set<String> browsers = AtlasDeviceScope.browserDevices(metadata);

        AtlasToolRow agent = tool("agent", "agent-a", 1, NOW, "desk-one", "browser-one");
        agent.getMcpDevices().put("server-a", new HashSet<>(Arrays.asList("desk-one", "browser-one")));
        List<AtlasToolRow> rows = Collections.singletonList(agent);

        assertEquals(2, AtlasDeviceScope.apply(rows, "all", browsers).get(0).getDeviceIds().size());
        AtlasToolRow desktop = AtlasDeviceScope.apply(rows, "desktop", browsers).get(0);
        assertEquals(new HashSet<>(Collections.singletonList("desk-one")), desktop.getDeviceIds());
        assertEquals(new HashSet<>(Collections.singletonList("desk-one")), desktop.getMcpDevices().get("server-a"));
        assertEquals(new HashSet<>(Collections.singletonList("browser-one")), AtlasDeviceScope.apply(rows, "browser", browsers).get(0).getDeviceIds());
        assertTrue(AtlasDeviceScope.apply(Collections.singletonList(tool("agent", "agent-b", 1, NOW, "browser-one")), "desktop", browsers).isEmpty());

        agent.getDeviceLastSeen().put("desk-one", NOW);
        agent.getDeviceLastSeen().put("browser-one", NOW - DAY);
        assertEquals(Collections.singletonMap("desk-one", NOW), desktop(rows, browsers).getDeviceLastSeen());
        // A scoped copy keeps every other field of the row.
        assertEquals("agent-a", desktop(rows, browsers).getName());
        assertEquals(agent.getRiskScore(), desktop(rows, browsers).getRiskScore(), 0);
    }

    private static AtlasToolRow desktop(List<AtlasToolRow> rows, Set<String> browsers) {
        return AtlasDeviceScope.apply(rows, "desktop", browsers).get(0);
    }

    @Test
    public void agentRowsAreAgents() {
        for (String agent : Arrays.asList("claude-vs-code", "authorized-agent", "geminicli")) {
            assertEquals(agent, "agents", AtlasCategory.of(tool("agent", agent, 1, NOW, "d1")));
        }
    }

    @Test
    public void knownIdeAgentsAreIdeAssistants() {
        for (String ide : Arrays.asList("cursor", "windsurf", "claude1")) {
            assertEquals(ide, "ide", AtlasCategory.of(tool("agent", ide, 1, NOW, "d1")));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void statusFilterLeavesOutToolsThatCannotBeReviewed() {
        List<AtlasToolRow> rows = new ArrayList<>();
        rows.add(tool("agent", "agent-a", 1, NOW, "d1"));
        rows.add(tool("skill", "skill-a", 1, NOW, "d1"));
        AtlasDiscoveryService service = service(rows, new HashMap<>(), Collections.emptySet());

        AtlasDiscoveryService.Query unapproved = new AtlasDiscoveryService.Query();
        unapproved.setLimit(10);
        unapproved.setStatus("UNAPPROVED");
        Map<String, Object> page = service.pageTools(unapproved);
        assertEquals(1, page.get("total"));
        assertEquals("agent-a", ((List<Map<String, Object>>) page.get("tools")).get(0).get("name"));

        AtlasDiscoveryService.Query everything = new AtlasDiscoveryService.Query();
        everything.setLimit(10);
        assertEquals(2, service.pageTools(everything).get("total"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void detailListsEmployeesAndConnections() {
        AtlasToolRow agent = tool("agent", "agent-a", 2, NOW, "d1", "d2");
        agent.getMcpDevices().put("server-a", new HashSet<>(Arrays.asList("d1", "d2")));
        AtlasToolRow server = tool("service", "server-a", 1, NOW, "d1", "d2");
        server.setCapabilityCollectionIds(Arrays.asList(11, 12));
        AtlasDiscoveryService service = service(Arrays.asList(agent, server), new HashMap<>(), Collections.emptySet());

        Map<String, Object> agentDetail = service.detail("agent|agent-a");
        List<Map<String, Object>> users = (List<Map<String, Object>>) agentDetail.get("users");
        assertEquals(2, users.size());
        assertEquals("Platform", users.stream().filter(u -> "user.one".equals(u.get("username"))).findFirst().get().get("team"));
        assertEquals("mcpServers", agentDetail.get("connectionsKind"));
        assertEquals("server-a", ((List<Map<String, Object>>) agentDetail.get("connections")).get(0).get("name"));

        Map<String, Object> serverDetail = service.detail("service|server-a");
        assertEquals("agents", serverDetail.get("connectionsKind"));
        assertEquals("agent-a", ((List<Map<String, Object>>) serverDetail.get("connections")).get(0).get("name"));
        assertEquals(Arrays.asList(11, 12), serverDetail.get("capabilityCollectionIds"));
        assertTrue(((List<Integer>) agentDetail.get("capabilityCollectionIds")).isEmpty());
        assertTrue(service.detail("agent|missing") == null);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void topEmployeesAreTheMostRecentlyActiveFirst() {
        AtlasToolRow agent = tool("agent", "agent-a", 1, NOW - 90 * DAY, "d1", "d2");
        agent.getDeviceLastSeen().put("d1", NOW - 40 * DAY);
        agent.getDeviceLastSeen().put("d2", NOW - DAY);
        List<Map<String, Object>> users = (List<Map<String, Object>>) service(Collections.singletonList(agent), new HashMap<>(), Collections.emptySet())
                .detail("agent|agent-a").get("users");

        assertEquals("user.two", users.get(0).get("username"));
        assertEquals(NOW - DAY, users.get(0).get("lastUsed"));
        assertEquals("user.one", users.get(1).get("username"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void supplyChainCountsEachDeviceOncePerTeamAndSkipsUntaggedDevices() {
        // Both tools run on the same two devices: d1 belongs to a team, d2 has none and is left out.
        AtlasToolRow first = tool("agent", "agent-a", 1, NOW, "d1", "d2");
        AtlasToolRow second = tool("agent", "agent-b", 1, NOW, "d1", "d2");
        Map<String, Object> chain = section(service(Arrays.asList(first, second), new HashMap<>(), Collections.emptySet()).buildOverview(), "supplyChain");

        List<Map<String, Object>> teamNodes = ((List<List<Map<String, Object>>>) chain.get("columns")).get(0);
        assertEquals(1, teamNodes.size());
        assertEquals(1, teamNodes.get(0).get("devices"));
        assertFalse("".equals(teamNodes.get(0).get("label")));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void graphsListServersWithTheirStatus() {
        AtlasToolRow agent = tool("agent", "agent-a", 1, NOW, "d1");
        agent.getMcpDevices().put("Server-New", new HashSet<>(Collections.singletonList("d1")));
        AtlasToolRow server = tool("service", "server-new", 1, NOW, "d1");
        Map<String, AtlasAssetStatus> decisions = new HashMap<>();
        decisions.put("mcp-server|server-new", AtlasAssetStatus.BLOCKED);
        Map<String, Object> overview = service(Arrays.asList(agent, server), decisions, Collections.emptySet()).buildOverview();

        List<Map<String, Object>> columns = (List<Map<String, Object>>) section(overview, "agentMcpMatrix").get("servers");
        assertEquals(1, columns.size());
        assertEquals("server-new", columns.get(0).get("name"));
        assertEquals("BLOCKED", columns.get(0).get("status"));

        List<Map<String, Object>> serverNodes = ((List<List<Map<String, Object>>>) section(overview, "supplyChain").get("columns")).get(2);
        assertEquals(1, serverNodes.size());
        assertEquals("server-new", serverNodes.get(0).get("label"));
        assertEquals("BLOCKED", serverNodes.get(0).get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void decisionsArePagedRiskiestFirst() {
        List<AtlasToolRow> rows = new ArrayList<>();
        for (int i = 1; i <= 7; i++) rows.add(tool("agent", "agent-" + i, i, NOW, "d1"));
        AtlasDiscoveryService service = service(rows, new HashMap<>(), Collections.emptySet());

        Map<String, Object> first = service.pageDecisions(0, 3);
        Map<String, Object> second = service.pageDecisions(3, 3);
        Map<String, Object> last = service.pageDecisions(6, 3);

        assertEquals(7, first.get("total"));
        List<Map<String, Object>> firstRows = (List<Map<String, Object>>) first.get("rows");
        assertEquals(3, firstRows.size());
        assertEquals("agent-7", firstRows.get(0).get("name"));
        assertEquals("agent-4", ((List<Map<String, Object>>) second.get("rows")).get(0).get("name"));
        assertEquals(1, ((List<Map<String, Object>>) last.get("rows")).size());
        assertTrue(((List<Map<String, Object>>) service.pageDecisions(20, 3).get("rows")).isEmpty());
    }

    private static McpAuditInfo auditRow(String remarks, String markedBy) {
        McpAuditInfo row = new McpAuditInfo();
        row.setRemarks(remarks);
        row.setMarkedBy(markedBy);
        return row;
    }

    @Test
    public void auditRowsMapToTheFourStatuses() {
        assertEquals(AtlasAssetStatus.BLOCKED, AtlasAssetStatusService.statusOf(auditRow("Rejected", "user.one")));
        assertEquals(AtlasAssetStatus.APPROVED, AtlasAssetStatusService.statusOf(auditRow("Approved", "user.one")));
        assertEquals(AtlasAssetStatus.APPROVED, AtlasAssetStatusService.statusOf(auditRow("Conditionally Approved", "user.one")));
        assertEquals(AtlasAssetStatus.UNDER_REVIEW, AtlasAssetStatusService.statusOf(auditRow("Under review", "user.one")));
        // A blank decision is unreviewed, whoever touched it.
        assertEquals(AtlasAssetStatus.UNAPPROVED, AtlasAssetStatusService.statusOf(auditRow("", "user.one")));
        assertEquals(AtlasAssetStatus.UNAPPROVED, AtlasAssetStatusService.statusOf(auditRow("", "")));
        assertEquals(AtlasAssetStatus.UNAPPROVED, AtlasAssetStatusService.statusOf(auditRow(null, null)));
    }

    @Test
    public void expiredConditionalApprovalShowsAsBlocked() {
        McpAuditInfo expired = auditRow("Conditionally Approved", "user.one");
        expired.setApprovalConditions(Collections.singletonMap("expiresAt", 1));
        McpAuditInfo active = auditRow("Conditionally Approved", "user.one");
        active.setApprovalConditions(Collections.singletonMap("expiresAt", Integer.MAX_VALUE));
        assertEquals(AtlasAssetStatus.BLOCKED, AtlasAssetStatusService.statusOf(expired));
        assertEquals(AtlasAssetStatus.APPROVED, AtlasAssetStatusService.statusOf(active));
    }

    @Test
    public void decisionPrioritiesFollowTheAuditPage() {
        McpAuditInfo expired = auditRow("Conditionally Approved", "user.one");
        expired.setApprovalConditions(Collections.singletonMap("expiresAt", 1));
        int blocked = AtlasAssetStatusService.priorityOf(auditRow("Rejected", "user.one"));
        int approved = AtlasAssetStatusService.priorityOf(auditRow("Approved", "user.one"));
        int expiredConditional = AtlasAssetStatusService.priorityOf(expired);
        int underReview = AtlasAssetStatusService.priorityOf(auditRow("Under review", "user.one"));
        int pending = AtlasAssetStatusService.priorityOf(auditRow("", ""));
        // A lower number wins when rows disagree: an approved row beats an expired one, which beats under review.
        assertTrue(blocked < approved && approved < expiredConditional && expiredConditional < underReview && underReview < pending);
    }

    @Test
    public void serverRowsAreKeyedByServerNameAndOthersByWholeName() {
        McpAuditInfo server = auditRow("Approved", "user.one");
        server.setType(Constants.AKTO_MCP_SERVER_TAG);
        server.setResourceName("device1.sample-agent.Sample-Server");
        assertEquals("sample-server", AtlasAssetStatusService.serverOrResourceName(server).toLowerCase());
        McpAuditInfo browser = auditRow("Approved", "user.one");
        browser.setType(Constants.AKTO_BROWSER_LLM_TAG);
        browser.setResourceName("chat.example.com");
        assertEquals("chat.example.com", AtlasAssetStatusService.serverOrResourceName(browser));
        assertEquals(Constants.AKTO_BROWSER_LLM_TAG + "|chat.example.com", AtlasAssetStatusService.decisionKey(Constants.AKTO_BROWSER_LLM_TAG, "Chat.Example.com"));
    }

    @Test
    public void rowNamePatternsDoNotMatchOtherTools() {
        Pattern server = Pattern.compile(AtlasAssetStatusService.rowNamePattern(Constants.AKTO_MCP_SERVER_TAG, "sample"), Pattern.CASE_INSENSITIVE);
        assertTrue(server.matcher("device1.sample-agent.Sample").matches());
        assertTrue(server.matcher("sample").matches());
        assertFalse(server.matcher("my.sample").matches());
        assertFalse(server.matcher("device1.sample-agent.other-sample").matches());
        Pattern browser = Pattern.compile(AtlasAssetStatusService.rowNamePattern(Constants.AKTO_BROWSER_LLM_TAG, "example.com"), Pattern.CASE_INSENSITIVE);
        assertTrue(browser.matcher("Example.com").matches());
        assertFalse(browser.matcher("chat.example.com").matches());
        assertFalse(browser.matcher("exampleXcom").matches());
    }

    @Test
    public void deviceScopeDropsServersLeftOnNoDevice() {
        AtlasToolRow agent = tool("agent", "agent-a", 1, NOW, "desktop-one", "browser-one");
        Map<String, Set<String>> servers = new HashMap<>();
        servers.put("server-a", new HashSet<>(Collections.singletonList("desktop-one")));
        servers.put("server-b", new HashSet<>(Collections.singletonList("browser-one")));
        agent.setMcpDevices(servers);
        Set<String> browsers = new HashSet<>(Collections.singletonList("browser-one"));
        Map<String, Set<String>> desktop = AtlasDeviceScope.apply(Collections.singletonList(agent), "desktop", browsers).get(0).getMcpDevices();
        assertEquals(new HashSet<>(Collections.singletonList("server-a")), desktop.keySet());
    }

    @Test
    public void deviceScopeCountsFlagsOnTheScopedDevicesOnly() {
        AtlasToolRow agent = tool("agent", "agent-a", 1, NOW, "desktop-one", "browser-one");
        agent.setMisconfigured(true);
        agent.setMisconfiguredDevices(1);
        agent.setMisconfiguredDeviceIds(new HashSet<>(Collections.singletonList("browser-one")));
        agent.setPersonal(true);
        agent.setPersonalDevices(2);
        agent.setPersonalDeviceIds(new HashSet<>(Arrays.asList("desktop-one", "browser-one")));
        Set<String> browsers = new HashSet<>(Collections.singletonList("browser-one"));
        AtlasToolRow desktop = AtlasDeviceScope.apply(Collections.singletonList(agent), "desktop", browsers).get(0);
        assertFalse(desktop.isMisconfigured());
        assertEquals(0, desktop.getMisconfiguredDevices());
        assertTrue(desktop.isPersonal());
        assertEquals(1, desktop.getPersonalDevices());
        AtlasToolRow all = AtlasDeviceScope.apply(Collections.singletonList(agent), "all", browsers).get(0);
        assertTrue(all.isMisconfigured());
        assertEquals(2, all.getPersonalDevices());
    }

    @Test
    public void serverRowNamesIncludeTheNameAfterTheDevicePrefix() {
        assertEquals(Arrays.asList("dev.agent.name", "name"), AtlasAssetStatusService.serverNamesOf("Dev.Agent.Name"));
        assertEquals(Collections.singletonList("name"), AtlasAssetStatusService.serverNamesOf("Name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void personalAccountsCountOnlyUsersOnThePersonalDevices() {
        AtlasToolRow agent = tool("agent", "agent-a", 1, NOW, "d1", "d2");
        agent.setPersonal(true);
        agent.setPersonalDeviceIds(new HashSet<>(Collections.singletonList("d1")));
        Map<String, Object> tiles = section(service(Collections.singletonList(agent), new HashMap<>(), Collections.emptySet()).buildOverview(), "tiles");
        Map<String, Object> agents = ((List<Map<String, Object>>) tiles.get("categories")).stream()
                .filter(category -> "agents".equals(category.get("id"))).findFirst().get();
        assertEquals(1, agents.get("personalAccounts"));
    }
}
