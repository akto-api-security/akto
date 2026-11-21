package com.akto.dto;

import static org.junit.Assert.assertEquals;
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
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.testing.custom_groups.UnauthenticatedEndpoint;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

/**
 * The runtime decides in memory whether a collection is agentic; the dashboard and usage metering
 * decide in Mongo. Both must agree for every tag combination, or ingestion gating and billing
 * would treat the same collection differently.
 */
public class TestApiCollectionAgentic extends MongoBasedTest {

    private static final String[][] TAGS = {
            {Constants.AKTO_MCP_SERVER_TAG, "v"},
            {Constants.AKTO_GEN_AI_TAG, "v"},
            {Constants.AKTO_AI_AGENT_CALLER_TAG, "v"},
            {Constants.AKTO_DAST_TAG, "v"},
            {Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE},
    };

    private static ApiCollection collection(int id, int tagMask) {
        ApiCollection c = new ApiCollection(id, "c" + id, Context.now(), new HashSet<>(), "c" + id, 0, false, true);
        List<CollectionTags> tags = new ArrayList<>();
        for (int i = 0; i < TAGS.length; i++) {
            if ((tagMask & (1 << i)) != 0) {
                tags.add(new CollectionTags(Context.now(), TAGS[i][0], TAGS[i][1], CollectionTags.TagSource.KUBERNETES));
            }
        }
        c.setTagsList(tags);
        return c;
    }

    @Before
    public void reset() {
        Context.resetContextThreadLocals();
        Context.accountId.set(ACCOUNT_ID);
        ApiCollectionsDao.instance.getMCollection().drop();
    }

    @Test
    public void inMemoryCheckMatchesTheMongoAgenticFilterForEveryTagCombination() {
        List<ApiCollection> all = new ArrayList<>();
        for (int mask = 0; mask < (1 << TAGS.length); mask++) {
            all.add(collection(80000 + mask, mask));
        }
        all.add(new ApiCollection(UnauthenticatedEndpoint.UNAUTHENTICATED_MCP_TOOLS_GROUP_ID, "unauth", Context.now(),
                new HashSet<>(), "unauth", 0, false, true));
        ApiCollectionsDao.instance.insertMany(all);

        Set<Integer> inMongo = UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.AGENTIC);
        // The dashboard context lists the unauthenticated MCP tools group; it is not an agentic
        // collection for counting or limits, so the in-memory check deliberately leaves it out.
        assertTrue(inMongo.remove(UnauthenticatedEndpoint.UNAUTHENTICATED_MCP_TOOLS_GROUP_ID));
        Set<Integer> inMemory = new HashSet<>();
        for (ApiCollection c : all) {
            if (c.isAgenticCollection()) {
                inMemory.add(c.getId());
            }
        }

        assertEquals(inMongo, inMemory);
        // guard against a vacuous pass: both sides really contain agentic collections
        assertTrue(inMemory.size() > 1);
    }

    @Test
    public void endpointSourceCollectionIsNeverAgentic() {
        ApiCollection c = collection(1, 0b10011); // mcp + genai + endpoint-source
        assertFalse(c.isAgenticCollection());
    }

    @Test
    public void aiAgentCallerCollectionIsAgentic() {
        assertTrue(collection(2, 0b00100).isAgenticCollection());
    }

    @Test
    public void dastAndPlainCollectionsAreNotAgentic() {
        assertFalse(collection(3, 0b01000).isAgenticCollection());
        assertFalse(collection(4, 0).isAgenticCollection());
    }
}
