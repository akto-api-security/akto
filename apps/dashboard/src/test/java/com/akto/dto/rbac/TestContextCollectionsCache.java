package com.akto.dto.rbac;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

public class TestContextCollectionsCache extends MongoBasedTest {

    private static final int FIRST_ID = 91001;
    private static final int SECOND_ID = 91002;
    private static final int MCP_ID = 91003;

    private void seed(int id, String name, String tagKey) {
        ApiCollection collection = new ApiCollection(id, name, Context.now(), new HashSet<>(), name, 0, false, true);
        List<CollectionTags> tags = new ArrayList<>();
        if (tagKey != null) {
            tags.add(new CollectionTags(Context.now(), tagKey, "v", CollectionTags.TagSource.KUBERNETES));
        }
        collection.setTagsList(tags);
        ApiCollectionsDao.instance.insertOne(collection);
    }

    private void clearCache() {
        for (CONTEXT_SOURCE source : CONTEXT_SOURCE.values()) {
            UsersCollectionsList.deleteContextCollectionsForUser(ACCOUNT_ID, source);
        }
    }

    @Before
    public void setUp() {
        Context.resetContextThreadLocals();
        Context.accountId.set(ACCOUNT_ID);
        ApiCollectionsDao.instance.getMCollection().drop();
        clearCache();
        seed(FIRST_ID, "orders-api", null);
    }

    @After
    public void tearDown() {
        clearCache();
    }

    @Test
    public void secondCallWithinTtlIsServedFromCache() {
        Set<Integer> first = UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API);
        seed(SECOND_ID, "payments-api", null);
        Set<Integer> second = UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API);

        assertEquals(Collections.singleton(FIRST_ID), first);
        assertSame(first, second);
    }

    @Test
    public void invalidationMakesNewCollectionsVisible() {
        UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API);
        seed(SECOND_ID, "payments-api", null);
        UsersCollectionsList.deleteContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API);

        Set<Integer> refreshed = UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API);

        assertTrue(refreshed.contains(FIRST_ID));
        assertTrue(refreshed.contains(SECOND_ID));
    }

    @Test
    public void invalidatingOneSourceKeepsTheOthersCached() {
        seed(MCP_ID, "tools-mcp", Constants.AKTO_MCP_SERVER_TAG);
        Set<Integer> mcpBefore = UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.MCP);
        UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API);

        UsersCollectionsList.deleteContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API);

        assertSame(mcpBefore, UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.MCP));
    }

    @Test
    public void entriesAreKeptPerSource() {
        seed(MCP_ID, "tools-mcp", Constants.AKTO_MCP_SERVER_TAG);

        Set<Integer> api = UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API);
        Set<Integer> mcp = UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.MCP);

        assertTrue(api.contains(FIRST_ID));
        assertFalse(api.contains(MCP_ID));
        assertTrue(mcp.contains(MCP_ID));
        assertFalse(mcp.contains(FIRST_ID));
        assertSame(api, UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API));
        assertSame(mcp, UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.MCP));
    }

    @Test
    public void accountsDoNotShareEntries() {
        int otherAccount = ACCOUNT_ID + 1;
        Set<Integer> mine = UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API);
        try {
            Context.accountId.set(otherAccount);
            ApiCollectionsDao.instance.getMCollection().drop();
            seed(SECOND_ID, "other-account-api", null);
            UsersCollectionsList.deleteContextCollectionsForUser(otherAccount, CONTEXT_SOURCE.API);

            Set<Integer> other = UsersCollectionsList.getContextCollectionsForUser(otherAccount, CONTEXT_SOURCE.API);

            assertEquals(Collections.singleton(SECOND_ID), other);
            assertEquals(Collections.singleton(FIRST_ID), mine);
            Context.accountId.set(ACCOUNT_ID);
            assertSame(mine, UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API));
        } finally {
            Context.accountId.set(otherAccount);
            ApiCollectionsDao.instance.getMCollection().drop();
            UsersCollectionsList.deleteContextCollectionsForUser(otherAccount, CONTEXT_SOURCE.API);
            Context.accountId.set(ACCOUNT_ID);
        }
    }

    @Test
    public void mismatchedAccountIsNeverCached() {
        Set<Integer> first = UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID + 1, CONTEXT_SOURCE.API);
        seed(SECOND_ID, "payments-api", null);
        Set<Integer> second = UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID + 1, CONTEXT_SOURCE.API);

        assertFalse(first.contains(SECOND_ID));
        assertTrue(second.contains(SECOND_ID));
    }

    @Test
    public void deletingForAnAccountClearsEverySource() {
        seed(MCP_ID, "tools-mcp", Constants.AKTO_MCP_SERVER_TAG);
        Set<Integer> api = UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API);
        Set<Integer> mcp = UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.MCP);

        UsersCollectionsList.deleteContextCollectionsForAccount(ACCOUNT_ID);

        assertNotSame(api, UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API));
        assertNotSame(mcp, UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.MCP));
    }

    @Test
    public void nullSourceSharesTheApiEntry() {
        Set<Integer> api = UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API);

        assertSame(api, UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, null));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void cachedSetCannotBeModifiedByCallers() {
        UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API).add(SECOND_ID);
    }

    @Test
    public void getContextCollectionsStaysUncached() {
        UsersCollectionsList.getContextCollectionsForUser(ACCOUNT_ID, CONTEXT_SOURCE.API);
        seed(SECOND_ID, "payments-api", null);

        assertTrue(UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.API).contains(SECOND_ID));
    }
}
