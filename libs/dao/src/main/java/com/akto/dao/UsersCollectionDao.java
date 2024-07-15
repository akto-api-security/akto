package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class UsersCollectionDao {
    private static final ConcurrentHashMap<Pair<Integer, Integer>, Pair<List<Integer>, Integer>> usersCollectionMap = new ConcurrentHashMap<>();
    private static final int EXPIRY_TIME = 30 * 60;

    public void deleteCollectionIdsFromCache(int userId, int accountId) {
        Pair<Integer, Integer> key = new Pair<>(userId, accountId);
        usersCollectionMap.remove(key);
    }

    public List<Integer> getCollectionsIdForUser(int userId, int accountId) {
        Pair<Integer, Integer> key = new Pair<>(userId, accountId);
        Pair<List<Integer>, Integer> collectionIdEntry = usersCollectionMap.get(key);
        List<Integer> collectionIds;
        if(collectionIdEntry == null || (Context.now() - collectionIdEntry.getSecond() > EXPIRY_TIME)) {
            List<Integer> collectionList = UsersDao.instance.getUserCollectionsById(userId, accountId);

            if(collectionList != null) {
                collectionIds = collectionList;
            } else {
                collectionIds = new ArrayList<>();
            }

            usersCollectionMap.put(key, new Pair<>(collectionIds, Context.now()));
        } else {
            collectionIds = collectionIdEntry.getFirst();
        }

        return collectionIds;
    }

}
