package com.akto.trafficFilter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.mongodb.client.model.Filters;

public class HostFilter {

    private static final String K8S_DEFAULT_HOST = "kubernetes.default.svc";
    private static final String LOCALHOST = "localhost";

    private static Map<Integer, Set<Integer>> collectionSet = new HashMap<>();

    public static Set<Integer> getCollectionSet(int accountId) {

        Set<Integer> ignoreCollectionSet = new HashSet<>();

        if (collectionSet.containsKey(accountId)) {
            return collectionSet.get(accountId);
        }

        List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                Filters.exists(ApiCollection.HOST_NAME, true));

        if (collections != null && !collections.isEmpty()) {
            for (ApiCollection collection : collections) {
                if (collection.getHostName() != null && (collection.getHostName().contains(K8S_DEFAULT_HOST) ||
                        collection.getHostName().contains(LOCALHOST))) {
                    ignoreCollectionSet.add(collection.getId());
                }
            }
        }
        collectionSet.put(accountId, ignoreCollectionSet);
        return collectionSet.get(accountId);
    }
}