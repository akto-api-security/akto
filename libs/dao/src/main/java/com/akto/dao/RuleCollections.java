package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.CustomRole;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Collections matched by custom roles' host / tag rules are saved on the role (ruleCollectionIds), so access
 * checks only read them and never run the rules. They are matched again in the background when collection
 * pages load (at most every REFRESH_INTERVAL seconds per account), and right away when a role, a collection or
 * its tags change. Caches are cleared only when a role's matches actually changed.
 */
public class RuleCollections {

    private static final Logger logger = LoggerFactory.getLogger(RuleCollections.class);
    static final int REFRESH_INTERVAL = 5 * 60;

    // accountId -> when this instance last refreshed it
    private static final ConcurrentHashMap<Integer, Integer> lastRefresh = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "rule-collections-refresher");
        thread.setDaemon(true);
        return thread;
    });

    /** The role's saved matches; a role never matched yet (e.g. saved before matches were stored) is matched now, once. */
    public static List<Integer> idsFor(CustomRole role) {
        return role.getRuleCollectionIds() != null ? role.getRuleCollectionIds() : match(role);
    }

    /** For pages that list collections: matches the account's rule roles again in the background, at most every REFRESH_INTERVAL. */
    public static void refreshInBackground(int accountId) {
        int now = Context.now();
        Integer last = lastRefresh.get(accountId);
        if (last != null && now - last < REFRESH_INTERVAL) {
            return;
        }
        if (last == null ? lastRefresh.putIfAbsent(accountId, now) == null : lastRefresh.replace(accountId, last, now)) {
            submit(accountId);
        }
    }

    /** After a collection is created or retagged: matches the account's rule roles again in the background, now. */
    public static void refreshSoon(int accountId) {
        lastRefresh.put(accountId, Context.now());
        submit(accountId);
    }

    private static void submit(int accountId) {
        try {
            executor.submit(() -> {
                Context.accountId.set(accountId);
                refresh(accountId);
            });
        } catch (Exception e) {
            logger.error("Error queueing rule collections refresh for account " + accountId + ": " + e.getMessage());
        }
    }

    /** Matches every rule role of the account again; clears access caches on all instances only if some matches changed. */
    public static void refresh(int accountId) {
        try {
            boolean changed = false;
            for (CustomRole role : CustomRoleDao.instance.findAll(Filters.exists(CustomRole.COLLECTION_RULES + ".0"))) {
                List<Integer> before = role.getRuleCollectionIds();
                List<Integer> after = match(role);
                changed |= before == null || !new HashSet<>(before).equals(new HashSet<>(after));
            }
            if (changed) {
                RbacCacheVersionDao.accessChanged(accountId);
            }
        } catch (Exception e) {
            logger.error("Error refreshing rule collections for account " + accountId + ": " + e.getMessage());
        }
    }

    /** Runs the role's rules and saves the result on the role when it changed. */
    public static List<Integer> match(CustomRole role) {
        List<Integer> saved = role.getRuleCollectionIds();
        List<Integer> ids;
        try {
            ids = role.getCollectionRules() == null ? new ArrayList<>() : new ArrayList<>(RBACDao.matchRules(role.getCollectionRules()));
            Collections.sort(ids);
        } catch (Exception e) {
            // e.g. a pattern Mongo rejects: keep what was saved; never matched means nothing, so the user stays limited
            logger.error("Error matching collection rules " + role.getCollectionRules() + " of role " + role.getName() + ": " + e.getMessage());
            ids = saved != null ? saved : new ArrayList<>();
        }
        if (saved == null || !saved.equals(ids)) {
            CustomRoleDao.instance.updateOne(Filters.eq(CustomRole._NAME, role.getName()), Updates.set(CustomRole.RULE_COLLECTION_IDS, ids));
        }
        role.setRuleCollectionIds(ids);
        return ids;
    }
}
