package com.akto.action;

import com.akto.MongoBasedTest;
import com.akto.dao.AgentUsersDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.AgenticUsers;
import com.akto.dto.ApiCollection;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.User;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// Audit Data -> AI Agents lists Agentic assets' agents; Allow / Block writes one ai-agent row per agent.
public class TestAiAgentAuditData extends MongoBasedTest {

    @Before
    public void setUp() throws Exception {
        Context.accountId.set(ACCOUNT_ID);
        // The tab runs in the Atlas (endpoint) context.
        Context.contextSource.set(CONTEXT_SOURCE.ENDPOINT);
        // Agentic assets caches its grouping per account for a minute; start from this test's collections.
        Field cache = AgenticObserveAction.class.getDeclaredField("classificationCache");
        cache.setAccessible(true);
        ((Map<?, ?>) cache.get(null)).clear();
        ApiCollectionsDao.instance.getMCollection().drop();
        McpAuditInfoDao.instance.getMCollection().drop();
        AgentUsersDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.insertMany(Arrays.asList(
                collection(1, "janedoe-mac.ai-agent.claudecli", "ai-agent", "claudecli"),
                collection(2, "janedoe-mac.ai-agent.claude-cli-user", "ai-agent", "claude-cli-user"),
                collection(3, "janedoe-mac.ai-agent.cursor", "ai-agent", "cursor"),
                // Host names another agent: guardrails must not block Claude traffic when Cursor is blocked.
                collection(4, "janedoe-mac.ai-agent.claude", "mcp-client", "cursor"),
                collection(5, "janedoe-mac.cursor.github", "mcp-server", "")));
    }

    // These are thread-locals: left set, they leak into whichever test class runs next in this JVM.
    @After
    public void clearContext() {
        Context.userId.remove();
        Context.contextSource.remove();
    }

    private static ApiCollection collection(int id, String host, String key, String value) {
        ApiCollection c = new ApiCollection(id, host, 0, new HashSet<>(), host, 0, false, true);
        List<CollectionTags> tags = new ArrayList<>();
        tags.add(new CollectionTags(0, "source", "ENDPOINT", CollectionTags.TagSource.AKTO));
        tags.add(new CollectionTags(0, key, value, CollectionTags.TagSource.AKTO));
        if (!"mcp-server".equals(key)) tags.add(new CollectionTags(0, Constants.AKTO_GEN_AI_TAG, "Gen AI", CollectionTags.TagSource.AKTO));
        c.setTagsList(tags);
        return c;
    }

    private static AuditDataAction action(Map<String, List> filters, String search) {
        AuditDataAction action = new AuditDataAction();
        User user = new User();
        user.setId(1);
        user.setLogin("janedoe@example.com");
        Map<String, Object> session = new HashMap<>();
        session.put("user", user);
        action.setSession(session);
        action.setAuditType(Constants.AKTO_AI_AGENT_TAG);
        action.setFilters(filters);
        action.setSearchString(search);
        action.setSortOrder(-1);
        return action;
    }

    private static List<String> names(AuditDataAction action) {
        List<String> out = new ArrayList<>();
        for (Object o : action.getAuditData()) out.add(((McpAuditInfo) o).getResourceName());
        Collections.sort(out);
        return out;
    }

    private static McpAuditInfo row(String name) {
        return McpAuditInfoDao.instance.findOne(Filters.and(
                Filters.eq(McpAuditInfo.TYPE, Constants.AKTO_AI_AGENT_TAG), Filters.eq(McpAuditInfo.RESOURCE_NAME, name)));
    }

    @Test
    public void listsAgenticAssetsAgentsAndStoresDecisions() {
        AuditDataAction list = action(new HashMap<>(), null);
        assertEquals("SUCCESS", list.fetchLlmAuditData());
        assertEquals(Arrays.asList("claude2", "cursor"), names(list));
        assertEquals(0, McpAuditInfoDao.instance.count(Filters.eq(McpAuditInfo.TYPE, Constants.AKTO_AI_AGENT_TAG)));

        AuditDataAction block = action(null, null);
        block.setResourceNames(Arrays.asList("claude2", "cursor", "not-an-agent"));
        block.setRemarks(McpAuditInfo.REMARKS_REJECTED);
        assertEquals("SUCCESS", block.updateLlmAuditData());
        assertEquals(2, McpAuditInfoDao.instance.count(Filters.eq(McpAuditInfo.TYPE, Constants.AKTO_AI_AGENT_TAG)));
        McpAuditInfo claude = row("claude2");
        assertEquals(McpAuditInfo.REMARKS_REJECTED, claude.getRemarks());
        assertEquals("janedoe@example.com", claude.getMarkedBy());
        assertEquals("ENDPOINT", claude.getContextSource());
        assertEquals(Arrays.asList("claude-cli-user", "claudecli"), sorted(claude.getRawValues()));
        assertEquals(Collections.singletonList("cursor"), row("cursor").getRawValues());
        assertNull(row("not-an-agent"));

        Map<String, List> blocked = new HashMap<>();
        blocked.put(McpAuditInfo.REMARKS, new ArrayList<>(Collections.singletonList(McpAuditInfo.REMARKS_REJECTED)));
        AuditDataAction blockedOnly = action(blocked, null);
        blockedOnly.fetchLlmAuditData();
        assertEquals(2, blockedOnly.getTotal());

        AuditDataAction search = action(new HashMap<>(), "Claude CLI");
        search.fetchLlmAuditData();
        assertEquals(Collections.singletonList("claude2"), names(search));

        AuditDataAction allow = action(null, null);
        allow.setResourceNames(Collections.singletonList("cursor"));
        allow.setRemarks(McpAuditInfo.REMARKS_APPROVED);
        assertEquals("SUCCESS", allow.updateLlmAuditData());
        assertEquals(McpAuditInfo.REMARKS_APPROVED, row("cursor").getRemarks());

        AuditDataAction invalid = action(null, null);
        invalid.setResourceNames(Collections.singletonList("claude2"));
        invalid.setRemarks("Pending");
        assertEquals("ERROR", invalid.updateLlmAuditData());
        assertEquals(McpAuditInfo.REMARKS_REJECTED, row("claude2").getRemarks());
    }

    @Test
    public void blockScopeIsStoredAndClearedOnAllow() {
        AgenticUsers jane = new AgenticUsers();
        jane.setUserName("janedoe");
        jane.setUserEmail("jane.doe@example.com");
        AgentUsersDao.instance.insertOne(jane);

        Map<String, List<String>> tags = new HashMap<>();
        tags.put("team", new ArrayList<>(Collections.singletonList("backend")));
        AuditDataAction block = action(null, null);
        block.setResourceNames(Collections.singletonList("cursor"));
        block.setRemarks(McpAuditInfo.REMARKS_REJECTED);
        block.setTargetTags(tags);
        block.setTargetDeviceIds(Collections.singletonList("janedoe-mac"));
        block.setTargetUserNames(Collections.singletonList("janedoe"));
        block.setNegatedTargetUserNames(true);
        assertEquals("SUCCESS", block.updateLlmAuditData());
        McpAuditInfo cursor = row("cursor");
        assertEquals(tags, cursor.getTargetTags());
        assertEquals(Collections.singletonList("janedoe-mac"), cursor.getTargetDeviceIds());
        assertEquals(1, cursor.getUserMetadata().size());
        assertEquals("jane.doe@example.com", cursor.getUserMetadata().get(0).getUserEmail());
        assertTrue(cursor.isNegatedTargetUserNames());

        // Same scope fields on the LLMs tab, by record id.
        McpAuditInfo site = new McpAuditInfo();
        site.setType(Constants.AKTO_BROWSER_LLM_TAG);
        site.setResourceName("chatgpt.com");
        McpAuditInfoDao.instance.insertOne(site);
        AuditDataAction blockSite = action(null, null);
        blockSite.setAuditType(Constants.AKTO_BROWSER_LLM_TAG);
        blockSite.setHexIds(Collections.singletonList(site.getId().toHexString()));
        blockSite.setRemarks(McpAuditInfo.REMARKS_REJECTED);
        blockSite.setTargetTags(tags);
        assertEquals("SUCCESS", blockSite.updateLlmAuditData());
        assertEquals(tags, McpAuditInfoDao.instance.findOne(Filters.eq(McpAuditInfo.RESOURCE_NAME, "chatgpt.com")).getTargetTags());

        // Allowing clears the scope, so a later block starts from everyone.
        AuditDataAction allow = action(null, null);
        allow.setResourceNames(Collections.singletonList("cursor"));
        allow.setRemarks(McpAuditInfo.REMARKS_APPROVED);
        allow.setTargetTags(tags);
        assertEquals("SUCCESS", allow.updateLlmAuditData());
        cursor = row("cursor");
        assertNull(cursor.getTargetTags());
        assertNull(cursor.getTargetDeviceIds());
        assertNull(cursor.getUserMetadata());
        assertFalse(cursor.isNegatedTargetUserNames());
    }

    @Test
    public void mcpServerBlockScopeCascadesToItsTools() {
        McpAuditInfo server = new McpAuditInfo();
        server.setType(Constants.AKTO_MCP_SERVER_TAG);
        server.setResourceName("janedoe-mac.cursor.github");
        server.setHostCollectionId(42);
        McpAuditInfo tool = new McpAuditInfo();
        tool.setType("mcp-tool");
        tool.setResourceName("list_repos");
        tool.setHostCollectionId(42);
        McpAuditInfoDao.instance.insertMany(Arrays.asList(server, tool));

        Map<String, List<String>> tags = new HashMap<>();
        tags.put("team", new ArrayList<>(Collections.singletonList("backend")));
        AuditDataAction block = action(null, null);
        block.setHexIds(Collections.singletonList(server.getId().toHexString()));
        block.setCascadeHostCollectionIds(Collections.singletonList(42));
        block.setRemarks(McpAuditInfo.REMARKS_REJECTED);
        block.setTargetTags(tags);
        assertEquals("SUCCESS", block.updateAuditData());
        for (McpAuditInfo row : McpAuditInfoDao.instance.findAll(Filters.eq(McpAuditInfo.HOST_COLLECTION_ID, 42))) {
            assertEquals(McpAuditInfo.REMARKS_REJECTED, row.getRemarks());
            assertEquals(tags, row.getTargetTags());
        }

        AuditDataAction allow = action(null, null);
        allow.setHexIds(Collections.singletonList(server.getId().toHexString()));
        allow.setCascadeHostCollectionIds(Collections.singletonList(42));
        allow.setRemarks(McpAuditInfo.REMARKS_APPROVED);
        assertEquals("SUCCESS", allow.updateAuditData());
        for (McpAuditInfo row : McpAuditInfoDao.instance.findAll(Filters.eq(McpAuditInfo.HOST_COLLECTION_ID, 42))) {
            assertEquals(McpAuditInfo.REMARKS_APPROVED, row.getRemarks());
            assertNull(row.getTargetTags());
        }
    }

    @Test
    public void mergedServerRowShowsTheBlockScopeNotANewerRows() {
        Map<String, List<String>> tags = new HashMap<>();
        tags.put("team", new ArrayList<>(Collections.singletonList("backend")));
        McpAuditInfo blocked = new McpAuditInfo();
        blocked.setType(Constants.AKTO_MCP_SERVER_TAG);
        blocked.setResourceName("janedoe-mac.cursor.github");
        blocked.setContextSource(CONTEXT_SOURCE.ENDPOINT.name());
        blocked.setLastDetected(100);
        blocked.setRemarks(McpAuditInfo.REMARKS_REJECTED);
        blocked.setTargetTags(tags);
        McpAuditInfo pending = new McpAuditInfo();
        pending.setType(Constants.AKTO_MCP_SERVER_TAG);
        pending.setResourceName("samlee-mac.cursor.github");
        pending.setContextSource(CONTEXT_SOURCE.ENDPOINT.name());
        pending.setLastDetected(200);
        McpAuditInfoDao.instance.insertMany(Arrays.asList(blocked, pending));

        Context.userId.set(1);
        AuditDataAction merged = action(new HashMap<>(), null);
        merged.setMergeMcpServers(true);
        assertEquals("SUCCESS", merged.fetchAuditData());
        assertEquals(1, merged.getAuditData().size());
        Map<?, ?> row = (Map<?, ?>) merged.getAuditData().get(0);
        assertEquals(McpAuditInfo.REMARKS_REJECTED, row.get(McpAuditInfo.REMARKS));
        assertEquals(tags, row.get(McpAuditInfo.TARGET_TAGS));
    }

    private static List<String> sorted(List<String> values) {
        List<String> out = new ArrayList<>(values);
        Collections.sort(out);
        return out;
    }
}
