package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.rbac.UsersCollectionsList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Roles, custom roles and collection access are cached on each dashboard instance. Every change bumps a
 * per-account version here, and each instance asks every CHECK_INTERVAL seconds which accounts changed
 * since it last asked, dropping its cached access for those. One small indexed read per instance per
 * interval, whatever the number of accounts; writes only on access changes.
 */
public class RbacCacheVersionDao extends CommonContextDao<BasicDBObject> {

    public static final RbacCacheVersionDao instance = new RbacCacheVersionDao();

    private static final Logger logger = LoggerFactory.getLogger(RbacCacheVersionDao.class);
    private static final String VERSION = "version";
    private static final String UPDATED_AT = "updatedAt";
    static final int CHECK_INTERVAL = 10;
    // changes are looked up a little further back than the last check, for clock differences between instances
    private static final int CLOCK_SKEW = 60;

    // accountId -> version this instance has seen
    private static final ConcurrentHashMap<Integer, Long> seenVersions = new ConcurrentHashMap<>();
    private static final AtomicInteger lastCheck = new AtomicInteger(0);
    private static volatile boolean indexCreated = false;

    /** Call after any change to a user's roles, a custom role or collection grants in the account. */
    public static void accessChanged(int accountId) {
        clearLocalCaches(accountId);
        try {
            BasicDBObject updated = instance.getMCollection().findOneAndUpdate(Filters.eq("_id", accountId),
                    Updates.combine(Updates.inc(VERSION, 1L), Updates.set(UPDATED_AT, Context.now())),
                    new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
            seenVersions.put(accountId, versionOf(updated));
        } catch (Exception e) {
            // other instances pick the change up when their caches expire
            logger.error("Error bumping rbac cache version for account " + accountId + ": " + e.getMessage());
        }
    }

    /** Drops this instance's cached access for accounts changed on other instances. Cheap: one read per CHECK_INTERVAL seconds. */
    public static void syncIfChanged(int accountId) {
        int now = Context.now();
        int previous = lastCheck.get();
        if (now - previous < CHECK_INTERVAL || !lastCheck.compareAndSet(previous, now)) {
            return;
        }
        try {
            ensureIndex();
            // the first check after start-up has nothing cached from before, so it only needs recent changes
            int since = (previous == 0 ? now : previous) - CLOCK_SKEW;
            for (BasicDBObject doc : instance.getMCollection().find(Filters.gte(UPDATED_AT, since))) {
                Object id = doc.get("_id");
                if (!(id instanceof Number)) continue;
                int changedAccount = ((Number) id).intValue();
                long version = versionOf(doc);
                Long seen = seenVersions.put(changedAccount, version);
                if (seen == null || seen != version) {
                    clearLocalCaches(changedAccount);
                }
            }
        } catch (Exception e) {
            logger.error("Error reading rbac cache versions: " + e.getMessage());
        }
    }

    private static void ensureIndex() {
        if (!indexCreated) {
            MCollection.createIndexIfAbsent(instance.getDBName(), instance.getCollName(), new String[]{UPDATED_AT}, false);
            indexCreated = true;
        }
    }

    static void clearLocalCaches(int accountId) {
        RBACDao.clearAccountCache(accountId);
        CustomRoleDao.clearRoleCache(accountId);
        UsersCollectionsList.deleteAccountCollectionIdsFromCache(accountId);
    }

    static void resetSeenVersions() {
        seenVersions.clear();
        lastCheck.set(0);
    }

    private static long versionOf(BasicDBObject doc) {
        Object version = doc == null ? null : doc.get(VERSION);
        return version instanceof Number ? ((Number) version).longValue() : 0L;
    }

    @Override
    public String getCollName() {
        return "rbac_cache_versions";
    }

    @Override
    public Class<BasicDBObject> getClassT() {
        return BasicDBObject.class;
    }
}
