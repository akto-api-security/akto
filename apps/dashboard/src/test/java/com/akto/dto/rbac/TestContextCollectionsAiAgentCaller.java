package com.akto.dto.rbac;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;

/**
 * getContextCollections is what actually files a collection under a product: API Security excludes
 * the agentic tags, Argus (AGENTIC) includes them. Classifying ai-agent-caller in AgenticObserveUtil
 * is not enough on its own - the collection is filtered out here, before the classifier ever runs,
 * so it stayed in API Security and never appeared in Argus.
 */
public class TestContextCollectionsAiAgentCaller extends MongoBasedTest {

    private static final int CALLER_ID = 90001;
    private static final int GEN_AI_ID = 90002;
    private static final int PLAIN_ID = 90003;
    private static final int MCP_ID = 90004;

    private void seed(int id, String name, String tagKey) {
        ApiCollection c = new ApiCollection(id, name, Context.now(), new HashSet<>(), name, 0, false, true);
        List<CollectionTags> tags = new ArrayList<>();
        if (tagKey != null) {
            tags.add(new CollectionTags(Context.now(), tagKey, "v", CollectionTags.TagSource.KUBERNETES));
        }
        c.setTagsList(tags);
        ApiCollectionsDao.instance.insertOne(c);
    }

    @Before
    public void seedCollections() {
        // Earlier tests leave userId / contextSource set, which RBAC-filters ApiCollectionsDao reads.
        Context.resetContextThreadLocals();
        Context.accountId.set(ACCOUNT_ID);
        ApiCollectionsDao.instance.getMCollection().drop();
        seed(CALLER_ID, "payments-worker", Constants.AKTO_AI_AGENT_CALLER_TAG);
        seed(GEN_AI_ID, "llm-gateway", Constants.AKTO_GEN_AI_TAG);
        seed(MCP_ID, "tools-mcp", Constants.AKTO_MCP_SERVER_TAG);
        seed(PLAIN_ID, "orders-api", null);
    }

    @Test
    public void callerIsNotInApiSecurity() {
        Set<Integer> api = UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.API);
        assertFalse("an ai-agent-caller collection must not be filed under API Security",
                api.contains(CALLER_ID));
    }

    @Test
    public void callerIsInArgus() {
        Set<Integer> agentic = UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.AGENTIC);
        assertTrue("an ai-agent-caller collection must be filed under Argus", agentic.contains(CALLER_ID));
    }

    @Test
    public void ordinaryCollectionStaysInApiSecurityAndOutOfArgus() {
        assertTrue(UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.API).contains(PLAIN_ID));
        assertFalse(UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.AGENTIC).contains(PLAIN_ID));
    }

    @Test
    public void genAiAndMcpAreUnaffected() {
        Set<Integer> api = UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.API);
        Set<Integer> agentic = UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.AGENTIC);
        assertFalse(api.contains(GEN_AI_ID));
        assertFalse(api.contains(MCP_ID));
        assertTrue(agentic.contains(GEN_AI_ID));
        assertTrue(agentic.contains(MCP_ID));
    }

    @Test
    public void callerIsNotPulledIntoTheGenAiOrMcpProducts() {
        // It is agentic, but it is neither an LLM nor an MCP server - those tabs must stay accurate.
        assertFalse(UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.GEN_AI).contains(CALLER_ID));
        assertFalse(UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.MCP).contains(CALLER_ID));
    }

    @Test
    public void seededCollectionsExist() {
        assertTrue(ApiCollectionsDao.instance.findAll(new BasicDBObject()).size() >= 4);
    }
}
