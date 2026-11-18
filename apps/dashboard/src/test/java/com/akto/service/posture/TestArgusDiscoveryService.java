package com.akto.service.posture;

import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightUtil;
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
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// Pure: compose() takes everything it needs, so no Mongo is involved.
public class TestArgusDiscoveryService {

    private static final int NOW = 1_800_000_000;
    private static final int DAY = 86400;

    private final ArgusDiscoveryService service = new ArgusDiscoveryService();

    private static ApiCollection collection(int id, String host, int startTs, String... tags) {
        ApiCollection c = new ApiCollection(id, host, startTs, new HashSet<>(), host, 0, false, true);
        List<CollectionTags> tagList = new ArrayList<>();
        for (int i = 0; i < tags.length; i += 2) {
            tagList.add(new CollectionTags(0, tags[i], tags[i + 1], CollectionTags.TagSource.KUBERNETES));
        }
        c.setTagsList(tagList);
        return c;
    }

    private static ApiCollection agent(int id, String host, String... tags) {
        return collection(id, host, NOW - 90 * DAY, concat(new String[]{Constants.AKTO_GEN_AI_TAG, "AI Agent"}, tags));
    }

    private static ApiCollection server(int id, String host, String... tags) {
        return collection(id, host, NOW - 90 * DAY, concat(new String[]{Constants.AKTO_MCP_SERVER_TAG, "MCP Server"}, tags));
    }

    private static String[] concat(String[] a, String[] b) {
        String[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static InsightDataBundle bundle(List<ApiCollection> collections, List<GuardrailPolicies> policies,
                                            Set<String> allowlist, Map<Integer, List<String>> sensitive, List<McpAuditInfo> auditRows) {
        InsightContext ctx = new InsightContext(1_000_000, 1, CONTEXT_SOURCE.AGENTIC, 0, NOW);
        return new InsightDataBundle(ctx, collections, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                auditRows, policies, allowlist, sensitive,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                true, new ArrayList<>(), new HashMap<>(), null, null);
    }

    private static GuardrailPolicies policyForAll() {
        GuardrailPolicies p = new GuardrailPolicies();
        p.setApplyToAllServers(true);
        return p;
    }

    private BasicDBObject compose(List<ApiCollection> collections, List<GuardrailPolicies> policies, Set<String> allowlist,
                                  Map<Integer, List<String>> sensitive, Map<Integer, Integer> lastSeen) {
        return compose(collections, policies, allowlist, sensitive, lastSeen, new ArrayList<>());
    }

    private BasicDBObject compose(List<ApiCollection> collections, List<GuardrailPolicies> policies, Set<String> allowlist,
                                  Map<Integer, List<String>> sensitive, Map<Integer, Integer> lastSeen, List<McpAuditInfo> auditRows) {
        List<ApiCollection> assets = ArgusDiscoveryService.assetsIn(collections);
        return service.compose(assets, bundle(collections, policies, allowlist, sensitive, auditRows), lastSeen, 40, 7, NOW);
    }

    // Audit rows as the dashboard writes them: linked to the server by collection id, with no mcpHost.
    private static McpAuditInfo audit(int collectionId, String type, String remarks) {
        McpAuditInfo row = new McpAuditInfo();
        row.setHostCollectionId(collectionId);
        row.setType(type);
        row.setRemarks(remarks);
        return row;
    }

    private static BasicDBObject kpi(BasicDBObject result, String name) {
        return (BasicDBObject) ((BasicDBObject) result.get("kpis")).get(name);
    }

    @SuppressWarnings("unchecked")
    private static List<BasicDBObject> todos(BasicDBObject result) {
        return (List<BasicDBObject>) result.get("todos");
    }

    private static BasicDBObject todo(BasicDBObject result, String id) {
        return todos(result).stream().filter(t -> id.equals(t.getString("id"))).findFirst().orElse(null);
    }

    @Test
    public void countsAgentsServersAndWhatIsNewThisWeek() {
        ApiCollection fresh = collection(1, "fresh-agent", NOW - DAY, Constants.AKTO_GEN_AI_TAG, "AI Agent");
        BasicDBObject result = compose(Arrays.asList(fresh, agent(2, "old-agent"), server(3, "mcp-a"), server(4, "mcp-b")),
                Collections.singletonList(policyForAll()), new HashSet<>(), new HashMap<>(), new HashMap<>());

        assertEquals(2, kpi(result, "agents").getInt("value"));
        assertEquals(1, kpi(result, "agents").getInt("newThisWeek"));
        assertEquals(2, kpi(result, "mcpServers").getInt("value"));
        assertEquals(40L, kpi(result, "tools").getLong("value"));
        assertEquals(7L, kpi(result, "tools").getLong("privileged"));
    }

    @Test
    public void aDecisionOnTheServerRowMakesItReviewedEvenWithoutAnMcpHost() {
        List<McpAuditInfo> rows = Arrays.asList(
                audit(1, "mcp-server", "Approved"),
                audit(2, "mcp-server", "Rejected"),
                audit(3, "mcp-server", ""),
                audit(4, "mcp-server", "Under review"),
                audit(5, "mcp-tool", "Approved"));
        BasicDBObject result = compose(Arrays.asList(server(1, "approved-mcp"), server(2, "rejected-mcp"), server(3, "pending-mcp"),
                server(4, "in-review-mcp"), server(5, "only-a-tool-approved"), server(6, "never-looked-at")),
                Collections.singletonList(policyForAll()), new HashSet<>(), new HashMap<>(), new HashMap<>(), rows);

        assertEquals("approved and rejected are reviewed; pending, under review, a tool decision and no row are not",
                4, kpi(result, "mcpServers").getInt("unreviewed"));
    }

    @Test
    public void deactivatedAndEndpointCollectionsAreNotAssets() {
        ApiCollection gone = agent(1, "gone");
        gone.setDeactivated(true);
        ApiCollection device = agent(2, "laptop.ai-agent.codex", Constants.AKTO_ENDPOINT_SOURCE_TAG, "ENDPOINT");
        BasicDBObject result = compose(Arrays.asList(gone, device, agent(3, "kept")), Collections.singletonList(policyForAll()),
                new HashSet<>(), new HashMap<>(), new HashMap<>());

        assertEquals(1, ((BasicDBObject) result.get("funnel")).getInt("assets"));
    }

    @Test
    public void shadowAiNeedsBothNoReviewAndARiskSignal() {
        ApiCollection withData = agent(1, "sends-data");
        ApiCollection observedOnly = agent(2, "observed", Constants.AKTO_GUARDRAIL_MODE, Constants.AKTO_GUARDRAIL_MODE_OBSERVE);
        ApiCollection noSignal = agent(3, "quiet");
        ApiCollection approved = agent(4, "approved");
        Set<String> allowlist = new HashSet<>(Collections.singletonList(InsightUtil.governanceGroupingName(approved).toLowerCase()));
        Map<Integer, List<String>> sensitive = new HashMap<>();
        sensitive.put(1, Collections.singletonList("EMAIL"));
        sensitive.put(4, Collections.singletonList("EMAIL"));

        BasicDBObject result = compose(Arrays.asList(withData, observedOnly, noSignal, approved),
                Collections.singletonList(policyForAll()), allowlist, sensitive, new HashMap<>());

        assertEquals("the approved agent and the one with no signal are left out", 2, kpi(result, "shadowAi").getInt("value"));
        assertEquals(1, kpi(result, "shadowAi").getInt("sensitive"));
        assertEquals("sensitive data makes it critical", "CRITICAL", todo(result, "shadowAi").getString("severity"));
    }

    @Test
    public void shadowAiWithoutSensitiveDataIsHighWhenOnlyObserved() {
        BasicDBObject result = compose(Collections.singletonList(agent(1, "observed", Constants.AKTO_GUARDRAIL_MODE, Constants.AKTO_GUARDRAIL_MODE_OBSERVE)),
                Collections.singletonList(policyForAll()), new HashSet<>(), new HashMap<>(), new HashMap<>());

        assertEquals("HIGH", todo(result, "shadowAi").getString("severity"));
    }

    @Test
    public void anAgentAlsoSeenInlineIsNotObservedOnly() {
        ApiCollection both = agent(1, "both");
        both.getTagsList().add(new CollectionTags(0, Constants.AKTO_GUARDRAIL_MODE, Constants.AKTO_GUARDRAIL_MODE_OBSERVE, CollectionTags.TagSource.KUBERNETES));
        both.getTagsList().add(new CollectionTags(0, Constants.AKTO_GUARDRAIL_MODE, Constants.AKTO_GUARDRAIL_MODE_INLINE, CollectionTags.TagSource.KUBERNETES));

        BasicDBObject result = compose(Collections.singletonList(both), Collections.singletonList(policyForAll()),
                new HashSet<>(), new HashMap<>(), new HashMap<>());

        assertNull(todo(result, "shadowAi"));
    }

    @Test
    public void exposedMcpNeedsPublicAndUnauthenticated() {
        ApiCollection exposed = server(1, "open-mcp");
        Map<String, Object> scores = new HashMap<>();
        scores.put("accessAuth", 100);
        exposed.setPostureSubScores(scores);
        ApiCollection publicOnly = server(2, "public-but-authed");
        Map<String, Object> half = new HashMap<>();
        half.put("accessAuth", 50);
        publicOnly.setPostureSubScores(half);

        BasicDBObject result = compose(Arrays.asList(exposed, publicOnly), Collections.singletonList(policyForAll()),
                new HashSet<>(), new HashMap<>(), new HashMap<>());

        BasicDBObject item = todo(result, "exposedMcp");
        assertEquals(1, item.getInt("count"));
        assertEquals("1 MCP server reachable from the internet with no authentication", item.getString("title"));
        assertEquals("CRITICAL", item.getString("severity"));
    }

    @Test
    public void maliciousMcpIsASupplyChainItem() {
        BasicDBObject result = compose(Arrays.asList(server(1, "bad-mcp", Constants.AKTO_MALICIOUS_MCP_SERVER_TAG, "true"), server(2, "fine-mcp")),
                Collections.singletonList(policyForAll()), new HashSet<>(), new HashMap<>(), new HashMap<>());

        assertEquals(1, todo(result, "supplyChain").getInt("count"));
    }

    @Test
    public void dangerousToolFollowsTheDestructiveCutOff() {
        ApiCollection destructive = agent(1, "can-delete");
        destructive.setPostureSubScores(new HashMap<>(Collections.singletonMap("overprivilegedTools", 70)));
        ApiCollection fileWrite = agent(2, "writes-files");
        fileWrite.setPostureSubScores(new HashMap<>(Collections.singletonMap("overprivilegedTools", 55)));

        BasicDBObject result = compose(Arrays.asList(destructive, fileWrite), Collections.singletonList(policyForAll()),
                new HashSet<>(), new HashMap<>(), new HashMap<>());

        assertEquals(1, todo(result, "dangerousTool").getInt("count"));
    }

    @Test
    public void coverageListsAssetsWithNoPolicyAndCountsTheNewOnes() {
        ApiCollection fresh = collection(1, "new-agent", NOW - DAY, Constants.AKTO_GEN_AI_TAG, "AI Agent");
        BasicDBObject uncovered = compose(Arrays.asList(fresh, agent(2, "old-agent")), new ArrayList<>(), new HashSet<>(), new HashMap<>(), new HashMap<>());
        BasicDBObject covered = compose(Arrays.asList(fresh, agent(2, "old-agent")), Collections.singletonList(policyForAll()),
                new HashSet<>(), new HashMap<>(), new HashMap<>());

        assertEquals(2, todo(uncovered, "coverage").getInt("count"));
        assertEquals(Collections.singletonList("1 new this week"), todo(uncovered, "coverage").get("chips"));
        assertNull(todo(covered, "coverage"));
        assertEquals(2, ((BasicDBObject) covered.get("coverage")).getInt("withPolicy"));
    }

    @Test
    public void staleMeansOldAndNoRecentTraffic() {
        Map<Integer, Integer> lastSeen = new HashMap<>();
        lastSeen.put(1, NOW - 40 * DAY);
        lastSeen.put(2, NOW - DAY);
        // 3 has no traffic at all and 4 is only a week old, so it is too new to call stale.
        ApiCollection young = collection(4, "young", NOW - 7 * DAY, Constants.AKTO_GEN_AI_TAG, "AI Agent");

        BasicDBObject result = compose(Arrays.asList(agent(1, "idle"), agent(2, "busy"), agent(3, "never-seen"), young),
                Collections.singletonList(policyForAll()), new HashSet<>(), new HashMap<>(), lastSeen);

        assertEquals(2, todo(result, "stale").getInt("count"));
        assertEquals("only the busy one saw traffic in the last 30 days", 1, ((BasicDBObject) result.get("coverage")).getLong("withTraffic"));
    }

    @Test
    public void todosAreRankedBySeverityThenSize() {
        ApiCollection exposed = server(1, "open-mcp");
        exposed.setPostureSubScores(new HashMap<>(Collections.singletonMap("accessAuth", 100)));
        Map<Integer, Integer> lastSeen = new HashMap<>();
        lastSeen.put(2, NOW - 60 * DAY);

        BasicDBObject result = compose(Arrays.asList(exposed, agent(2, "idle")), new ArrayList<>(), new HashSet<>(), new HashMap<>(), lastSeen);

        List<String> order = new ArrayList<>();
        todos(result).forEach(t -> order.add(t.getString("id")));
        assertEquals(Arrays.asList("exposedMcp", "coverage", "stale"), order);
        assertEquals("the funnel counts the same list the page shows", order.size(), ((BasicDBObject) result.get("funnel")).getInt("todos"));
    }

    @Test
    public void aTodoNamesItsRiskiestAssetsFirstAndCapsTheList() {
        List<ApiCollection> many = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            ApiCollection c = agent(i, "agent-" + i);
            c.setPostureScore((double) i * 10);
            many.add(c);
        }
        BasicDBObject result = compose(many, new ArrayList<>(), new HashSet<>(), new HashMap<>(), new HashMap<>());

        @SuppressWarnings("unchecked")
        List<BasicDBObject> shown = (List<BasicDBObject>) todo(result, "coverage").get("assets");
        assertEquals(5, shown.size());
        assertEquals("the highest posture score comes first", 8, shown.get(0).getInt("id"));
        assertEquals(8, todo(result, "coverage").getInt("count"));
    }

    @Test
    public void platformsGroupBySourceAndCollapseInstanceIds() {
        BasicDBObject result = compose(Arrays.asList(
                agent(1, "a", "source", "AWS_BEDROCK"),
                agent(2, "b", "source", "aws-vpc_vpc-111"),
                agent(3, "c", "source", "aws-vpc_vpc-222"),
                agent(4, "d", "source", "source=k8s-svc_cards.production.svc.cluster.local"),
                agent(5, "e")),
                Collections.singletonList(policyForAll()), new HashSet<>(), new HashMap<>(), new HashMap<>());

        @SuppressWarnings("unchecked")
        List<BasicDBObject> platforms = (List<BasicDBObject>) result.get("platforms");
        List<String> names = new ArrayList<>();
        platforms.forEach(p -> names.add(p.getString("name") + ":" + p.getInt("total")));
        assertEquals(Arrays.asList("AWS VPC:2", "AWS Bedrock:1", "Kubernetes:1", "Other:1"), names);
    }

    @Test
    public void aCollectionWithNoHostNameDoesNotBreakTheOverview() {
        ApiCollection noHost = new ApiCollection(1, "connector-agent", NOW - 90 * DAY, new HashSet<>(), null, 0, false, true);
        noHost.setTagsList(new ArrayList<>(Collections.singletonList(
                new CollectionTags(0, Constants.AKTO_GEN_AI_TAG, "AI Agent", CollectionTags.TagSource.KUBERNETES))));

        BasicDBObject result = compose(Collections.singletonList(noHost), new ArrayList<>(), new HashSet<>(), new HashMap<>(), new HashMap<>());

        assertNotNull(result);
        assertFalse(todos(result).isEmpty());
    }

    @Test
    public void anEmptyAccountGivesZerosAndNoTodos() {
        BasicDBObject result = compose(new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashMap<>(), new HashMap<>());

        assertEquals(0, kpi(result, "agents").getInt("value"));
        assertTrue(todos(result).isEmpty());
        assertTrue(((List<?>) result.get("platforms")).isEmpty());
    }
}
