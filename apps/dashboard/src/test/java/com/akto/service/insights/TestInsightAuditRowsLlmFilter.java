package com.akto.service.insights;

import com.akto.MongoBasedTest;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.McpAuditInfo;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * loadAuditRows keeps master's contextSource scoping (current source, or legacy rows without one)
 * and leaves browser-llm rows out of component insights.
 */
public class TestInsightAuditRowsLlmFilter extends MongoBasedTest {

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        McpAuditInfoDao.instance.getMCollection().drop();
        McpAuditInfoDao.instance.getMCollection().withDocumentClass(BasicDBObject.class).insertMany(Arrays.asList(
                row("mcp-server", "endpoint-server", CONTEXT_SOURCE.ENDPOINT.name()),
                row("mcp-server", "api-server", CONTEXT_SOURCE.API.name()),
                row("mcp-server", "legacy-server", null),
                row(McpAuditInfo.TYPE_AGENT_SKILL, "endpoint-skill", CONTEXT_SOURCE.ENDPOINT.name()),
                row(Constants.AKTO_BROWSER_LLM_TAG, "chatgpt.com", CONTEXT_SOURCE.ENDPOINT.name()),
                row(Constants.AKTO_BROWSER_LLM_TAG, "claude.ai", null)));
    }

    private static BasicDBObject row(String type, String resourceName, String contextSource) {
        BasicDBObject doc = new BasicDBObject(McpAuditInfo.TYPE, type)
                .append(McpAuditInfo.RESOURCE_NAME, resourceName)
                .append(McpAuditInfo.LAST_DETECTED, Context.now());
        if (contextSource != null) doc.append(McpAuditInfo.CONTEXT_SOURCE, contextSource);
        return doc;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> load(CONTEXT_SOURCE contextSource) throws Exception {
        Method m = InsightDataLoader.class.getDeclaredMethod("loadAuditRows", CONTEXT_SOURCE.class);
        m.setAccessible(true);
        List<McpAuditInfo> rows = (List<McpAuditInfo>) m.invoke(new InsightDataLoader(), contextSource);
        return rows.stream().map(McpAuditInfo::getResourceName).collect(Collectors.toSet());
    }

    @Test
    public void endpointKeepsCurrentAndLegacyRowsWithoutLlms() throws Exception {
        assertEquals(new HashSet<>(Arrays.asList("endpoint-server", "legacy-server", "endpoint-skill")),
                load(CONTEXT_SOURCE.ENDPOINT));
    }

    @Test
    public void otherContextSeesOnlyItsOwnAndLegacyRows() throws Exception {
        assertEquals(new HashSet<>(Arrays.asList("api-server", "legacy-server")), load(CONTEXT_SOURCE.API));
    }

    @Test
    public void nullContextSeesOnlyLegacyRowsWithoutLlms() throws Exception {
        assertEquals(new HashSet<>(Arrays.asList("legacy-server")), load(null));
    }
}
