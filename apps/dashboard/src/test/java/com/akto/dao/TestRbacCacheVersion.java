package com.akto.dao;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dto.RBAC;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

/* A role change made on one dashboard instance reaches the others through the per-account version. */
public class TestRbacCacheVersion extends MongoBasedTest {

    static final int USER = 301;

    @Before
    public void setup() {
        Context.accountId.set(ACCOUNT_ID);
        Context.contextSource.set(CONTEXT_SOURCE.API);
        RBACDao.instance.getMCollection().drop();
        RbacCacheVersionDao.instance.getMCollection().drop();
        RbacCacheVersionDao.resetSeenVersions();
        RBACDao.instance.insertOne(new RBAC(USER, null, ACCOUNT_ID, Collections.singletonMap("API", "MEMBER")));
        RBACDao.clearAccountCache(ACCOUNT_ID);
    }

    static String cachedRole() {
        return RBACDao.getCurrentRBACForUser(USER, ACCOUNT_ID).getScopeRoleMapping().get("API");
    }

    // what another instance does when it changes access: write the role and bump the version, without touching our caches
    static void changeOnAnotherInstance(String role) {
        RBACDao.instance.updateOne(Filters.eq(RBAC.USER_ID, USER), Updates.set(RBAC.SCOPE_ROLE_MAPPING, Collections.singletonMap("API", role)));
        RbacCacheVersionDao.instance.getMCollection().updateOne(Filters.eq("_id", ACCOUNT_ID), Updates.inc("version", 1L),
                new com.mongodb.client.model.UpdateOptions().upsert(true));
    }

    @SuppressWarnings("unchecked")
    static void lastCheckedLongAgo() throws Exception {
        Field field = RbacCacheVersionDao.class.getDeclaredField("seenVersions");
        field.setAccessible(true);
        Map<Integer, Pair<Long, Integer>> seen = (ConcurrentHashMap<Integer, Pair<Long, Integer>>) field.get(null);
        Pair<Long, Integer> entry = seen.get(ACCOUNT_ID);
        seen.put(ACCOUNT_ID, new Pair<>(entry.getFirst(), Context.now() - RbacCacheVersionDao.CHECK_INTERVAL - 1));
    }

    @Test
    public void testChangeOnAnotherInstanceIsPickedUp() throws Exception {
        RbacCacheVersionDao.syncIfChanged(ACCOUNT_ID);
        assertEquals("MEMBER", cachedRole());

        changeOnAnotherInstance("GUEST");
        RbacCacheVersionDao.syncIfChanged(ACCOUNT_ID); // checked moments ago: no read, cache kept
        assertEquals("MEMBER", cachedRole());

        lastCheckedLongAgo();
        RbacCacheVersionDao.syncIfChanged(ACCOUNT_ID); // new version seen: cached access dropped
        assertEquals("GUEST", cachedRole());
    }

    @Test
    public void testUnchangedVersionKeepsTheCache() throws Exception {
        RbacCacheVersionDao.syncIfChanged(ACCOUNT_ID);
        assertEquals("MEMBER", cachedRole());
        // a direct write without a version bump (as no code path does) stays cached until the cache expires
        RBACDao.instance.updateOne(Filters.eq(RBAC.USER_ID, USER), Updates.set(RBAC.SCOPE_ROLE_MAPPING, Collections.singletonMap("API", "GUEST")));
        lastCheckedLongAgo();
        RbacCacheVersionDao.syncIfChanged(ACCOUNT_ID);
        assertEquals("MEMBER", cachedRole());
    }

    @Test
    public void testLocalChangeClearsAndBumps() {
        assertEquals("MEMBER", cachedRole());
        RBACDao.instance.updateOne(Filters.eq(RBAC.USER_ID, USER), Updates.set(RBAC.SCOPE_ROLE_MAPPING, Collections.singletonMap("API", "GUEST")));
        RbacCacheVersionDao.accessChanged(ACCOUNT_ID);
        assertEquals("GUEST", cachedRole());
        BasicDBObject version = RbacCacheVersionDao.instance.getMCollection().find(Filters.eq("_id", ACCOUNT_ID)).first();
        assertEquals(1L, ((Number) version.get("version")).longValue());
        RbacCacheVersionDao.accessChanged(ACCOUNT_ID);
        version = RbacCacheVersionDao.instance.getMCollection().find(Filters.eq("_id", ACCOUNT_ID)).first();
        assertEquals(2L, ((Number) version.get("version")).longValue());
    }
}
