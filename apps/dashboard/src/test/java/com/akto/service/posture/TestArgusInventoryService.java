package com.akto.service.posture;

import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.nhi_governance.NhiIdentity;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.type.URLMethods;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// Pure: compose() takes everything it needs, so no Mongo is involved.
public class TestArgusInventoryService {

    private static final int NOW = 1_800_000_000;
    private static final int DAY = 86400;

    private static ApiCollection agent(int id, String host, double risk, int createdDaysAgo, String... tags) {
        ApiCollection c = new ApiCollection(id, host, NOW - createdDaysAgo * DAY, new HashSet<>(), host, 0, false, true);
        List<CollectionTags> tagList = new ArrayList<>();
        tagList.add(new CollectionTags(0, Constants.AKTO_GEN_AI_TAG, "AI Agent", CollectionTags.TagSource.KUBERNETES));
        for (int i = 0; i < tags.length; i += 2) tagList.add(new CollectionTags(0, tags[i], tags[i + 1], CollectionTags.TagSource.KUBERNETES));
        c.setTagsList(tagList);
        c.setPostureScore(risk);
        return c;
    }

    private static ApiInfo tool(int collectionId, String name, String capability) {
        ApiInfo info = new ApiInfo(collectionId, "/mcp/tools/call/" + name, URLMethods.Method.POST);
        info.setToolInfo(new ApiInfo.ToolInfo(capability, NOW));
        return info;
    }

    private static GuardrailPolicies policyForAll() {
        GuardrailPolicies p = new GuardrailPolicies();
        p.setApplyToAllServers(true);
        return p;
    }

    private static ArgusInventoryService inventory(List<ApiCollection> collections, boolean withPolicy,
                                                   Map<Integer, Integer> lastSeen, Map<Integer, List<ApiInfo>> tools) {
        InsightContext ctx = new InsightContext(1_000_000, 1, CONTEXT_SOURCE.AGENTIC, 0, NOW);
        InsightDataBundle bundle = new InsightDataBundle(ctx, collections, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new ArrayList<>(), withPolicy ? Collections.singletonList(policyForAll()) : new ArrayList<>(), new HashSet<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                true, new ArrayList<>(), new HashMap<>(), null, null);
        List<ArgusInventoryRow> rows = ArgusInventorySource.compose(ArgusDiscoveryService.assetsIn(collections), bundle, lastSeen, tools, NOW);
        return new ArgusInventoryService(rows, NOW);
    }

    private static Map<Integer, Integer> seenToday(int... ids) {
        Map<Integer, Integer> seen = new HashMap<>();
        for (int id : ids) seen.put(id, NOW - 100);
        return seen;
    }

    private static ArgusInventoryService.Query query() {
        ArgusInventoryService.Query q = new ArgusInventoryService.Query();
        q.setLimit(50);
        return q;
    }

    @SuppressWarnings("unchecked")
    private static List<String> names(BasicDBObject page) {
        return ((List<BasicDBObject>) page.get("agents")).stream().map(r -> r.getString("name")).collect(Collectors.toList());
    }

    @Test
    public void postureGroupsFollowRiskAndDormancy() {
        // 1: high and active, 2: high but quiet, 3: low and covered with traffic, 4: low and not covered.
        List<ApiCollection> all = Arrays.asList(agent(1, "high-active", 80, 90), agent(2, "high-quiet", 80, 90), agent(3, "low-ok", 10, 90));
        BasicDBObject summary = inventory(all, true, seenToday(1, 3), new HashMap<>()).buildSummary();

        Map<String, Long> groups = new HashMap<>();
        ((List<BasicDBObject>) summary.get("posture")).forEach(g -> groups.put(g.getString("key"), g.getLong("count")));
        assertEquals(1L, (long) groups.get("atRisk"));
        assertEquals("a high-risk asset with no traffic needs attention, not an alarm", 1L, (long) groups.get("attention"));
        assertEquals(1L, (long) groups.get("healthy"));
        assertEquals(2L, ((BasicDBObject) summary.get("counts")).getLong("high"));
        assertEquals(1L, ((BasicDBObject) summary.get("counts")).getLong("dormant"));
    }

    @Test
    public void teamsComeFromTagsAndNoTeamLeadsTheList() {
        List<ApiCollection> all = Arrays.asList(
                agent(1, "a", 80, 90, "team", "Payments"),
                agent(2, "b", 20, 90, "team", "Payments", "team", "Support"),
                agent(3, "c", 70, 90));
        BasicDBObject summary = inventory(all, true, seenToday(1, 2, 3), new HashMap<>()).buildSummary();

        @SuppressWarnings("unchecked")
        List<BasicDBObject> teams = (List<BasicDBObject>) summary.get("teams");
        assertEquals("No owning team", teams.get(0).getString("name"));
        assertEquals(1, teams.get(0).getInt("total"));
        assertEquals("Payments", teams.get(1).getString("name"));
        assertEquals("an agent with two owners counts for each", 2, teams.get(1).getInt("total"));
        assertEquals(1L, ((BasicDBObject) summary.get("counts")).getLong("noTeamHigh"));
    }

    @Test
    public void thePageFiltersSortsSearchesAndPages() {
        List<ApiCollection> all = Arrays.asList(
                agent(1, "refund-agent", 90, 90, "team", "Payments", "source", "N8N"),
                agent(2, "faq-bot", 30, 90, "source", "N8N"),
                agent(3, "ops-agent", 60, 3));
        Map<Integer, List<ApiInfo>> tools = new HashMap<>();
        tools.put(1, Arrays.asList(tool(1, "issue_refund", "CRITICAL_RESOURCE_WRITE"), tool(1, "get_order", "SAFE")));
        ArgusInventoryService inv = inventory(all, true, seenToday(1, 2, 3), tools);

        assertEquals("riskiest first by default", Arrays.asList("refund-agent", "ops-agent", "faq-bot"), names(inv.pageAgents(query())));

        ArgusInventoryService.Query noTeam = query();
        noTeam.setTeam("none");
        assertEquals(Arrays.asList("ops-agent", "faq-bot"), names(inv.pageAgents(noTeam)));

        ArgusInventoryService.Query newThisWeek = query();
        newThisWeek.setFlag("new");
        assertEquals(Collections.singletonList("ops-agent"), names(inv.pageAgents(newThisWeek)));

        ArgusInventoryService.Query platform = query();
        platform.setPlatforms(Collections.singletonList("n8n"));
        assertEquals(Arrays.asList("refund-agent", "faq-bot"), names(inv.pageAgents(platform)));

        ArgusInventoryService.Query byTool = query();
        byTool.setTool("issue_refund");
        assertEquals(Collections.singletonList("refund-agent"), names(inv.pageAgents(byTool)));

        ArgusInventoryService.Query search = query();
        search.setText("PAYMENTS");
        assertEquals("search covers the team", Collections.singletonList("refund-agent"), names(inv.pageAgents(search)));

        ArgusInventoryService.Query oldestFirst = query();
        oldestFirst.setSortKey("createdTs");
        oldestFirst.setSortOrder(-1);
        assertEquals("sortOrder -1 is descending", "ops-agent", names(inv.pageAgents(oldestFirst)).get(0));

        ArgusInventoryService.Query second = query();
        second.setSkip(1);
        second.setLimit(1);
        BasicDBObject page = inv.pageAgents(second);
        assertEquals(Collections.singletonList("ops-agent"), names(page));
        assertEquals("total is the whole match, not the page", 3, page.getInt("total"));
    }

    @Test
    public void toolsAreCountedAndOnlyNonSafeOnesArePrivileged() {
        Map<Integer, List<ApiInfo>> tools = new HashMap<>();
        tools.put(1, Arrays.asList(tool(1, "issue_refund", "CRITICAL_RESOURCE_WRITE"), tool(1, "get_order", "SAFE"),
                new ApiInfo(1, "/mcp/tools/list", URLMethods.Method.POST)));
        ArgusInventoryService inv = inventory(Collections.singletonList(agent(1, "a", 50, 90)), true, seenToday(1), tools);

        @SuppressWarnings("unchecked")
        BasicDBObject row = ((List<BasicDBObject>) inv.pageAgents(query()).get("agents")).get(0);
        assertEquals(3, row.getInt("tools"));
        assertEquals(1, row.getInt("privileged"));

        @SuppressWarnings("unchecked")
        List<String> toolNames = ((List<BasicDBObject>) inv.buildSummary().get("tools")).stream().map(t -> t.getString("name")).collect(Collectors.toList());
        assertTrue("privileged tools come first, and the MCP list method is not a tool name", toolNames.equals(Arrays.asList("issue_refund", "get_order")));
    }

    @Test
    public void recentCarriesWhetherAPolicyIsAttached() {
        BasicDBObject summary = inventory(Collections.singletonList(agent(1, "a", 10, 1)), false, seenToday(1), new HashMap<>()).buildSummary();

        @SuppressWarnings("unchecked")
        BasicDBObject recent = ((List<BasicDBObject>) summary.get("recent")).get(0);
        assertEquals(0, recent.getInt("policies"));
        assertEquals(1L, ((BasicDBObject) summary.get("counts")).getLong("noPolicy"));
    }

    @Test
    public void quietIdentitiesCarryWhatTheRowNeeds() {
        NhiIdentity identity = new NhiIdentity("svc-token", "API_KEY", "AGENTIC", "billing-agent");
        identity.setLastUsedAt(NOW - 60 * DAY);
        identity.setAccessLevel("write");

        BasicDBObject quiet = ArgusInventorySource.quietOf(7, Collections.singletonList(identity));

        assertEquals(7L, quiet.getLong("total"));
        @SuppressWarnings("unchecked")
        BasicDBObject item = ((List<BasicDBObject>) quiet.get("items")).get(0);
        assertEquals("svc-token", item.getString("name"));
        assertEquals("billing-agent", item.getString("agentName"));
        assertEquals(NOW - 60 * DAY, item.getInt("lastUsedTs"));
    }

    private static ArgusInventoryRow.ArgusInventoryRowBuilder row() {
        return ArgusInventoryRow.builder().teams(Collections.emptyList()).toolNames(Collections.emptyList()).policies(1);
    }

    @Test
    public void lastUsedBucketsFollowTheDaysSinceTraffic() {
        assertEquals("never", row().seenTs(0).build().lastUsed(NOW));
        assertEquals("today", row().seenTs(NOW - 100).build().lastUsed(NOW));
        assertEquals("w", row().seenTs(NOW - 7 * DAY).build().lastUsed(NOW));
        assertEquals("m", row().seenTs(NOW - 8 * DAY).build().lastUsed(NOW));
        assertEquals("q", row().seenTs(NOW - 90 * DAY).build().lastUsed(NOW));
        assertEquals("old", row().seenTs(NOW - 91 * DAY).build().lastUsed(NOW));
    }

    @Test
    public void everyFlagIsOneRuleOverTheRowsFacts() {
        ArgusInventoryRow shadowWithData = row().shadow(true).sensitive(true).build();
        assertTrue(shadowWithData.matchesFlag("comboShadow"));
        assertTrue("shadow alone is not the combination", !row().shadow(true).build().matchesFlag("comboShadow"));
        assertTrue(row().exposed(true).dangerous(true).build().matchesFlag("comboExposed"));
        assertTrue(row().dormant(true).privileged(2).build().matchesFlag("comboDormant"));
        assertTrue(row().unreviewed(true).privileged(2).build().matchesFlag("comboUnreviewed"));
        assertTrue(row().policies(0).build().matchesFlag("noPolicy"));
        assertTrue(row().build().matchesFlag("noTeam"));
        assertTrue("an unknown flag matches nothing", !row().build().matchesFlag("nope"));
    }

    @Test
    public void postureIsAtRiskOnlyWithAHighScoreAndTraffic() {
        assertEquals(ArgusInventoryRow.AT_RISK, row().risk(80).build().posture());
        assertEquals("a high score with no traffic needs attention", ArgusInventoryRow.ATTENTION, row().risk(80).dormant(true).build().posture());
        assertEquals("a warning sign needs attention", ArgusInventoryRow.ATTENTION, row().risk(10).exposed(true).build().posture());
        assertEquals("no policy needs attention", ArgusInventoryRow.ATTENTION, row().risk(10).policies(0).build().posture());
        assertEquals(ArgusInventoryRow.HEALTHY, row().risk(10).build().posture());
    }

    @Test
    public void anUnknownFlagOnThePageMatchesNothing() {
        ArgusInventoryService inv = inventory(Collections.singletonList(agent(1, "a", 50, 90)), true, seenToday(1), new HashMap<>());
        ArgusInventoryService.Query unknown = query();
        unknown.setFlag("nope");
        assertEquals(0, inv.pageAgents(unknown).getInt("total"));
    }
}
