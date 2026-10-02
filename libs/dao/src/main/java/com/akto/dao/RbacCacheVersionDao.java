package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.util.Pair;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Roles, custom roles and collection access are cached on each dashboard instance. Every change bumps a
 * per-account version here, and each instance drops its cached access for that account when it sees a
 * new version, so a change applies on every instance within CHECK_INTERVAL seconds.
 * One small read per account per instance every CHECK_INTERVAL seconds; writes only on access changes.
 */
public class RbacCacheVersionDao extends CommonContextDao<BasicDBObject> {

    public static final RbacCacheVersionDao instance = new RbacCacheVersionDao();

    private static final Logger logger = LoggerFactory.getLogger(RbacCacheVersionDao.class);
    private static final String VERSION = "version";
    static final int CHECK_INTERVAL = 10;

    // accountId -> (version seen, when it was checked)
    private static final ConcurrentHashMap<Integer, Pair<Long, Integer>> seenVersions = new ConcurrentHashMap<>();

    /** Call after any change to a user's roles, a custom role or collection grants in the account. */
    public static void accessChanged(int accountId) {
        clearLocalCaches(accountId);
        try {
            BasicDBObject updated = instance.getMCollection().findOneAndUpdate(Filters.eq("_id", accountId),
                    Updates.inc(VERSION, 1L), new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
            seenVersions.put(accountId, new Pair<>(versionOf(updated), Context.now()));
        } catch (Exception e) {
            // other instances pick the change up when their caches expire
            logger.error("Error bumping rbac cache version for account " + accountId + ": " + e.getMessage());
        }
    }

    /** Drops this instance's cached access for the account if another instance changed it. Cheap: checks at most every CHECK_INTERVAL seconds. */
    public static void syncIfChanged(int accountId) {
        int now = Context.now();
        Pair<Long, Integer> seen = seenVersions.get(accountId);
        if (seen != null && now - seen.getSecond() < CHECK_INTERVAL) {
            return;
        }
        long version = seen == null ? -1 : seen.getFirst();
        try {
            version = versionOf(instance.getMCollection().find(Filters.eq("_id", accountId)).first());
            if (seen == null || seen.getFirst() != version) {
                clearLocalCaches(accountId);
            }
        } catch (Exception e) {
            logger.error("Error reading rbac cache version for account " + accountId + ": " + e.getMessage());
        }
        seenVersions.put(accountId, new Pair<>(version, now));
    }

    static void clearLocalCaches(int accountId) {
        RBACDao.clearAccountCache(accountId);
        CustomRoleDao.clearRoleCache(accountId);
        UsersCollectionsList.deleteAccountCollectionIdsFromCache(accountId);
    }

    static void resetSeenVersions() {
        seenVersions.clear();
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
