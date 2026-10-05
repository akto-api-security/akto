package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.CustomRole;
import com.akto.util.Pair;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import java.util.concurrent.ConcurrentHashMap;

public class CustomRoleDao extends AccountsContextDao<CustomRole> {

    public static final CustomRoleDao instance = new CustomRoleDao();

    // Per-request access checks read custom roles through this cache. Every role write clears it on all
    // instances (RbacCacheVersionDao.accessChanged), so it can be kept long
    private static final ConcurrentHashMap<String, Pair<CustomRole, Integer>> roleCache = new ConcurrentHashMap<>();
    private static final int ROLE_CACHE_EXPIRY_TIME = 15 * 60;

    public CustomRole findRoleByNameCached(String roleName) {
        if (roleName == null) {
            return null;
        }
        String key = Context.accountId.get() + "|" + roleName;
        Pair<CustomRole, Integer> entry = roleCache.get(key);
        if (entry == null || Context.now() - entry.getSecond() > ROLE_CACHE_EXPIRY_TIME) {
            entry = new Pair<>(findRoleByName(roleName), Context.now());
            roleCache.put(key, entry);
        }
        return entry.getFirst();
    }

    public static void clearRoleCache() {
        roleCache.clear();
    }

    public static void clearRoleCache(int accountId) {
        String accountPrefix = accountId + "|";
        roleCache.keySet().removeIf(key -> key.startsWith(accountPrefix));
    }

    public void createIndicesIfAbsent() {
        boolean exists = false;
        String dbName = Context.accountId.get()+"";
        MongoDatabase db = clients[0].getDatabase(dbName);
        for (String col: db.listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            db.createCollection(getCollName());
        }

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { CustomRole._NAME }, false);
    }

    public CustomRole findRoleByName(String roleName) {
        return instance.findOne(Filters.eq(CustomRole._NAME, roleName));
    }

    @Override
    public String getCollName() {
        return "custom_roles";
    }

    @Override
    public Class<CustomRole> getClassT() {
        return CustomRole.class;
    }

}
