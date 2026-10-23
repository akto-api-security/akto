package com.akto.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;

/**
 * A collection is only included in Argus when AgenticObserveUtil.findTypeTag returns something -
 * AgenticObserveAction.classifyAllGroups does `if (typeTag == null) continue;`. Services tagged
 * ai-agent-caller (seen calling an AI asset) were not recognised, so they never reached Argus at all.
 */
public class TestAgenticObserveUtilAiAgentCaller {

    private ApiCollection collectionWith(String... tagKeys) {
        ApiCollection c = new ApiCollection(1, "svc", 0, new HashSet<>(), "svc", 0, false, true);
        List<CollectionTags> tags = new ArrayList<>();
        for (String key : tagKeys) {
            tags.add(new CollectionTags(0, key, "v", CollectionTags.TagSource.KUBERNETES));
        }
        c.setTagsList(tags);
        return c;
    }

    // ---------- reaching Argus at all ----------

    @Test
    public void aiAgentCallerIsRecognisedAsAType() {
        CollectionTags typeTag = AgenticObserveUtil.findTypeTag(collectionWith(Constants.AKTO_AI_AGENT_CALLER_TAG));
        assertNotNull("ai-agent-caller must yield a type tag or classifyAllGroups skips the collection",
                typeTag);
        assertEquals(Constants.AKTO_AI_AGENT_CALLER_TAG, typeTag.getKeyName());
    }

    @Test
    public void aiAgentCallerIsLabelledAnAiAgentNotAnMcpServer() {
        assertEquals("falling through to the default would mislabel the caller as an MCP Server",
                AgenticObserveUtil.CLIENT_TYPE_AI_AGENT,
                AgenticObserveUtil.getTypeFromCollection(collectionWith(Constants.AKTO_AI_AGENT_CALLER_TAG)));
    }

    // ---------- it must not outrank a collection's own classification ----------

    @Test
    public void mcpServerStillWinsOverAiAgentCaller() {
        ApiCollection c = collectionWith(Constants.AKTO_AI_AGENT_CALLER_TAG, Constants.AKTO_MCP_SERVER_TAG);
        assertEquals(Constants.AKTO_MCP_SERVER_TAG, AgenticObserveUtil.findTypeTag(c).getKeyName());
        assertEquals(AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER, AgenticObserveUtil.getTypeFromCollection(c));
    }

    @Test
    public void genAiStillWinsOverAiAgentCaller() {
        // Tag order reversed too: the caller tag must not win just by appearing first.
        for (List<String> order : Arrays.asList(
                Arrays.asList(Constants.AKTO_AI_AGENT_CALLER_TAG, Constants.AKTO_GEN_AI_TAG),
                Arrays.asList(Constants.AKTO_GEN_AI_TAG, Constants.AKTO_AI_AGENT_CALLER_TAG))) {
            ApiCollection c = collectionWith(order.toArray(new String[0]));
            assertEquals("gen-ai describes the collection itself and must take precedence",
                    Constants.AKTO_GEN_AI_TAG, AgenticObserveUtil.findTypeTag(c).getKeyName());
        }
    }

    @Test
    public void browserLlmStillWinsOverAiAgentCaller() {
        ApiCollection c = collectionWith(Constants.AKTO_AI_AGENT_CALLER_TAG, Constants.AKTO_BROWSER_LLM_TAG);
        assertEquals(Constants.AKTO_BROWSER_LLM_TAG, AgenticObserveUtil.findTypeTag(c).getKeyName());
        assertEquals(AgenticObserveUtil.CLIENT_TYPE_LLM, AgenticObserveUtil.getTypeFromCollection(c));
    }

    // ---------- nothing else changes ----------

    @Test
    public void ordinaryCollectionIsStillNotAgentic() {
        assertNull("a collection with no agentic tag must still be skipped by Argus",
                AgenticObserveUtil.findTypeTag(collectionWith("privatecloud.agoda.com/service", "envType")));
    }

    @Test
    public void genAiAloneIsUnaffected() {
        ApiCollection c = collectionWith(Constants.AKTO_GEN_AI_TAG);
        assertEquals(Constants.AKTO_GEN_AI_TAG, AgenticObserveUtil.findTypeTag(c).getKeyName());
        assertEquals(AgenticObserveUtil.CLIENT_TYPE_AI_AGENT, AgenticObserveUtil.getTypeFromCollection(c));
    }

    @Test
    public void collectionWithNoTagsIsStillSkipped() {
        ApiCollection c = new ApiCollection(2, "svc", 0, new HashSet<>(), "svc", 0, false, true);
        assertNull(AgenticObserveUtil.findTypeTag(c));
    }
}
