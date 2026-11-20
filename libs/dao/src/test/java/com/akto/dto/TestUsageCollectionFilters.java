package com.akto.dto;

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
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

/**
 * Usage metering's collection filters, checked against the dashboard contexts for every tag combination.
 */
public class TestUsageCollectionFilters extends MongoBasedTest {

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

    /*
     * The usage cron's mismatch alert compares ACTIVE_ENDPOINTS with the API and DAST contexts'
     * endpoint counts. The API context must therefore hold no agentic or endpoint-source
     * collection, and every collection ACTIVE_ENDPOINTS counts must be in the API or DAST context.
     */
    @Test
    public void apiContextHoldsOnlyCollectionsActiveEndpointsCounts() {
        List<ApiCollection> all = new ArrayList<>();
        for (int mask = 0; mask < (1 << TAGS.length); mask++) {
            all.add(collection(90000 + mask, mask));
        }
        ApiCollectionsDao.instance.insertMany(all);

        Set<Integer> api = UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.API);
        Set<Integer> dast = UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.DAST);
        Set<Integer> notBilled = new HashSet<>();
        for (ApiCollection c : ApiCollectionsDao.instance.findAll(UsersCollectionsList.agenticCollectionsFilter())) {
            notBilled.add(c.getId());
        }
        for (ApiCollection c : ApiCollectionsDao.instance.findAll(UsersCollectionsList.endpointSourceCollectionsFilter())) {
            notBilled.add(c.getId());
        }

        for (ApiCollection c : all) {
            if (notBilled.contains(c.getId())) {
                assertFalse("API context holds a collection ACTIVE_ENDPOINTS does not count: " + c.getTagsList(),
                        api.contains(c.getId()));
            } else {
                assertTrue("ACTIVE_ENDPOINTS counts a collection outside the API and DAST contexts: " + c.getTagsList(),
                        api.contains(c.getId()) || dast.contains(c.getId()));
            }
        }
        // guard against a vacuous pass
        assertTrue(api.size() > 0 && notBilled.size() > 1);
    }
}
