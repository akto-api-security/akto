package com.akto.dto.rbac;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.RBACDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.billing.Organization;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsersCollectionsList {
    private static final ConcurrentHashMap<Pair<Integer, Integer>, Pair<List<Integer>, Integer>> usersCollectionMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Pair<Integer, CONTEXT_SOURCE>, Pair<Set<Integer>, Integer>> contextCollectionsMap = new ConcurrentHashMap<>();
    private static final int EXPIRY_TIME = 15 * 60;
    private static final int CONTEXT_EXPIRY_TIME = 120;

    private static final Logger logger = LoggerFactory.getLogger(UsersCollectionsList.class);

    public static void deleteCollectionIdsFromCache(int userId, int accountId) {
        Pair<Integer, Integer> key = new Pair<>(userId, accountId);
        usersCollectionMap.remove(key);
    }

    public static final String RBAC_FEATURE = "RBAC_FEATURE";

    /*
     * Cases:
     * 1. For admin we save the list as null and
     * do not add any collection filter in db queries if the list comes to be null.
     * 2. For roles other than admin, we save the list as empty if no collections
     * are there and add the empty filter, so no data comes out.
     * 3. If no rbac is found, we treat it as least privilege account (empty array).
     * 4. If rbac feature not available, then, full access.
     */
    public static List<Integer> getCollectionsIdForUser(int userId, int accountId) {
        Pair<Integer, Integer> key = new Pair<>(userId, accountId);
        Pair<List<Integer>, Integer> collectionIdEntry = usersCollectionMap.get(key);
        List<Integer> collectionList = new ArrayList<>();
        
        if(collectionIdEntry == null || (Context.now() - collectionIdEntry.getSecond() > EXPIRY_TIME)) {
            Organization organization = OrganizationsDao.instance.findOne(
                Filters.in(Organization.ACCOUNTS, accountId));

            // air-gapped environment, full access.
            if (organization == null ||
                    organization.getFeatureWiseAllowed() == null ||
                    organization.getFeatureWiseAllowed().isEmpty()) {
                logger.info("UsersCollectionsList org details not available");
                collectionList = null;
            // feature accessible
            } else if (organization != null &&
                    organization.getFeatureWiseAllowed() != null &&
                    !organization.getFeatureWiseAllowed().isEmpty() &&
                    organization.getFeatureWiseAllowed().containsKey(RBAC_FEATURE) &&
                    organization.getFeatureWiseAllowed().get(RBAC_FEATURE).getIsGranted()) {
                logger.info("UsersCollectionsList rbac feature found");
                collectionList = RBACDao.instance.getUserCollectionsById(userId, accountId);
            // feature not accessible
            } else {
                collectionList = null;
            }

            usersCollectionMap.put(key, new Pair<>(collectionList, Context.now()));
        } else {
            collectionList = collectionIdEntry.getFirst();
        }

        // since this function is used everywhere for the queries, taking context collections into account here
        Set<Integer> contextCollections = getContextCollectionsForUser(accountId, Context.contextSource.get());
        if(collectionList == null) {
            collectionList = contextCollections.stream()
                .collect(Collectors.toList());
        } else if (contextCollections != null && !contextCollections.isEmpty()) {
            collectionList = collectionList.stream()
                .filter(contextCollections::contains)
                .collect(Collectors.toList());
        }
        return collectionList;
    }

    public static void deleteContextCollectionsForUser(int accountId, CONTEXT_SOURCE source) {
        if(source == null) {
            source = CONTEXT_SOURCE.API;
        }
        if(contextCollectionsMap.isEmpty()) {
            return;
        }
        Pair<Integer, CONTEXT_SOURCE> key = new Pair<>(accountId, source);
        contextCollectionsMap.remove(key);
    }

    public static Set<Integer> getContextCollectionsForUser(int accountId, CONTEXT_SOURCE source) {
        if(source == null) {
            source = CONTEXT_SOURCE.API;
        }
        Pair<Integer, CONTEXT_SOURCE> key = new Pair<>(accountId, source);
        Pair<Set<Integer>, Integer> collectionIdEntry = contextCollectionsMap.get(key);
        Set<Integer> collectionList = new HashSet<>();

        if (collectionIdEntry == null || (Context.now() - collectionIdEntry.getSecond() > CONTEXT_EXPIRY_TIME)) {
            collectionList = getContextCollections(source);
        } else {
            collectionList = collectionIdEntry.getFirst();
        }

        return collectionList;
    }

    public static Set<Integer> getContextCollections(CONTEXT_SOURCE source) {
        Set<Integer> collectionIds = new HashSet<>();
        Bson finalFilter = Filters.or(
            Filters.exists(ApiCollection.TAGS_STRING, false),
            Filters.nor(
                Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_MCP_SERVER_TAG)),
                Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_GEN_AI_TAG)),
                Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_DAST_TAG))
            )
        );
        switch (source) {
            case MCP:
                finalFilter = Filters.and(
                    Filters.exists(ApiCollection.TAGS_STRING),
                    Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_MCP_SERVER_TAG))
                );
                break;
            case GEN_AI:
                finalFilter = Filters.and(
                    Filters.exists(ApiCollection.TAGS_STRING),
                    Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_GEN_AI_TAG))
                );
                break;
            case AGENTIC:
                // For agentic context, include both MCP and GenAI collections
                // This should only be used when user has full agentic access (both limits configured)
                finalFilter = Filters.and(
                    Filters.exists(ApiCollection.TAGS_STRING),
                    Filters.or(
                        Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_MCP_SERVER_TAG)),
                        Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_GEN_AI_TAG))
                    )
                );
                break;
            case DAST:
                finalFilter = Filters.and(
                    Filters.exists(ApiCollection.TAGS_STRING),
                    Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_DAST_TAG))
                );
                break;
            default:
                break;
        }
        MongoCursor<ApiCollection> cursor = ApiCollectionsDao.instance.getMCollection().find(finalFilter).projection(Projections.include(Constants.ID)).iterator();
        while (cursor.hasNext()) {
            collectionIds.add(cursor.next().getId());
        }
        return collectionIds;
    }
}
