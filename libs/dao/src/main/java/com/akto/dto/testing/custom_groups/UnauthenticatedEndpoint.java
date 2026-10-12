package com.akto.dto.testing.custom_groups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiInfo.AuthType;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

public class UnauthenticatedEndpoint extends TestingEndpoints {

    private static int limit = 1000;
    private static final Logger logger = LoggerFactory.getLogger(UnauthenticatedEndpoint.class);

    @BsonIgnore
    private List<ApiInfoKey> apiInfos;

    public UnauthenticatedEndpoint() {
        super(Type.UNAUTHENTICATED, Operator.OR);
    }

    public List<ApiInfoKey> getApiInfos() {
        return apiInfos;
    }

    public void setApiInfos(List<ApiInfoKey> apiInfos) {
        this.apiInfos = apiInfos;
    }

    @Override
    public List<ApiInfoKey> returnApis() {
       return this.apiInfos;
    }

    @Override
    public boolean containsApi(ApiInfoKey key) {
        ApiInfo apiInfo = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(key));
        if (apiInfo == null) {
            return false;
        }
        apiInfo.calculateActualAuth();
        if (apiInfo.getActualAuthType() == null || apiInfo.getActualAuthType().isEmpty()) {
            return false;
        }
        Set<AuthType> authTypes = new HashSet<>(apiInfo.getActualAuthType());
        if (authTypes == null || authTypes.isEmpty()) {
            return false;
        }
        if (authTypes.contains(AuthType.UNAUTHENTICATED)) {
            return true;
        }
        return false;
    }

    private static Bson createApiFilters(CollectionType type, ApiInfoKey api) {

        String prefix = getFilterPrefix(type);

        return Filters.and(
                Filters.eq(prefix + SingleTypeInfo._URL, api.getUrl()),
                Filters.eq(prefix + SingleTypeInfo._METHOD, api.getMethod().toString()),
                Filters.in(SingleTypeInfo._COLLECTION_IDS, api.getApiCollectionId()));

    }

    public final static int UNAUTHENTICATED_GROUP_ID = 111_111_120;

    private static final Set<AuthType> UNAUTHENTICATED_AUTH_SET = Collections.singleton(AuthType.UNAUTHENTICATED);

    /** Full recompute: used on first run, once a day, and when a group is recomputed explicitly. */
    public static void updateCollections(){
        updateCollections(0, true);
    }

    /**
     * @param since only used when !full: apis whose lastSeen is older than this cannot have changed their auth types
     *              (auth types only change in runtime upserts, which bump lastSeen), so they are not looked at.
     * @param full  reset the group and rebuild it from every api info.
     */
    public static void updateCollections(int since, boolean full){
        Set<Integer> allowedCollectionIds = fetchAllowedCollectionIds();
        if (full) {
            fullUpdate(allowedCollectionIds);
        } else {
            incrementalUpdate(allowedCollectionIds, since);
        }
    }

    private static Set<Integer> fetchAllowedCollectionIds() {
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(
            Filters.and(
                UsersCollectionsList.getApiContextFilter(),
                Filters.ne(ApiCollection._TYPE, ApiCollection.Type.API_GROUP.toString())
            ), Projections.include("_id")
        );
        Set<Integer> ids = new HashSet<>();
        for (ApiCollection apiCollection : apiCollections) {
            ids.add(apiCollection.getId());
        }
        return ids;
    }

    private static void fullUpdate(Set<Integer> allowedCollectionIds) {
        ApiCollectionUsers.reset(UNAUTHENTICATED_GROUP_ID);

        Bson unauthenticatedFilter = Filters.in(
            ApiInfo.ALL_AUTH_TYPES_FOUND,
            Collections.singletonList(Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED))
        );
        Bson sort = Sorts.ascending(ApiInfo.ID_API_COLLECTION_ID, ApiInfo.ID_URL, ApiInfo.ID_METHOD);
        int accountId = Context.accountId.get();
        logger.info(String.format("AccountId: %d Starting full update of unauthenticated data collection", accountId));

        // single keyset scan over api_info instead of one query per api collection
        Bson filter = unauthenticatedFilter;
        int scanned = 0;
        while (true) {
            List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(filter, 0, limit, sort, Projections.include("_id"));
            if (apiInfos.isEmpty()) {
                break;
            }
            List<ApiInfoKey> keys = new ArrayList<>();
            for (ApiInfo apiInfo : apiInfos) {
                if (allowedCollectionIds.contains(apiInfo.getId().getApiCollectionId())) {
                    keys.add(apiInfo.getId());
                }
            }
            addToGroup(keys);
            scanned += apiInfos.size();

            if (apiInfos.size() < limit) {
                break;
            }
            ApiInfoKey last = apiInfos.get(apiInfos.size() - 1).getId();
            filter = Filters.and(unauthenticatedFilter,
                    ApiInfoDao.keysetAfter(last.getApiCollectionId(), last.getUrl(), last.getMethod().name()));
        }
        logger.info(String.format("AccountId: %d Finished full update of unauthenticated data collection, scanned: %d",
                accountId, scanned));
    }

    private static void incrementalUpdate(Set<Integer> allowedCollectionIds, int since) {
        int accountId = Context.accountId.get();
        logger.info(String.format("AccountId: %d Starting incremental update of unauthenticated data collection since %d",
                accountId, since));

        List<Integer> collectionIds = new ArrayList<>(allowedCollectionIds);
        int changed = 0;
        for (int i = 0; i < collectionIds.size(); i += limit) {
            List<Integer> chunk = collectionIds.subList(i, Math.min(i + limit, collectionIds.size()));
            Bson filter = Filters.and(
                Filters.in(ApiInfo.ID_API_COLLECTION_ID, chunk),
                Filters.gte(ApiInfo.LAST_SEEN, since)
            );

            List<ApiInfoKey> unauthenticated = new ArrayList<>();
            List<ApiInfoKey> authenticated = new ArrayList<>();
            try (MongoCursor<ApiInfo> cursor = ApiInfoDao.instance.getMCollection().find(filter)
                    .projection(Projections.include("_id", ApiInfo.ALL_AUTH_TYPES_FOUND))
                    .batchSize(limit).cursor()) {
                while (cursor.hasNext()) {
                    ApiInfo apiInfo = cursor.next();
                    Set<Set<AuthType>> authTypesFound = apiInfo.getAllAuthTypesFound();
                    boolean isUnauthenticated = authTypesFound != null && authTypesFound.contains(UNAUTHENTICATED_AUTH_SET);
                    (isUnauthenticated ? unauthenticated : authenticated).add(apiInfo.getId());

                    if (unauthenticated.size() >= limit) {
                        addToGroup(unauthenticated);
                        changed += unauthenticated.size();
                        unauthenticated.clear();
                    }
                    if (authenticated.size() >= limit) {
                        removeFromGroup(authenticated);
                        changed += authenticated.size();
                        authenticated.clear();
                    }
                }
            }
            addToGroup(unauthenticated);
            removeFromGroup(authenticated);
            changed += unauthenticated.size() + authenticated.size();
        }
        logger.info(String.format("AccountId: %d Finished incremental update of unauthenticated data collection, apis checked: %d",
                accountId, changed));
    }

    private static void addToGroup(List<ApiInfoKey> keys) {
        if (keys.isEmpty()) {
            return;
        }
        UnauthenticatedEndpoint endpoint = new UnauthenticatedEndpoint();
        endpoint.setApiInfos(new ArrayList<>(keys));
        ApiCollectionUsers.addToCollectionsForCollectionId(Collections.singletonList(endpoint), UNAUTHENTICATED_GROUP_ID);
    }

    private static void removeFromGroup(List<ApiInfoKey> keys) {
        if (keys.isEmpty()) {
            return;
        }
        UnauthenticatedEndpoint endpoint = new UnauthenticatedEndpoint();
        endpoint.setApiInfos(new ArrayList<>(keys));
        ApiCollectionUsers.removeApisFromCollectionId(Collections.singletonList(endpoint), UNAUTHENTICATED_GROUP_ID);
    }


    @Override
    public Bson createFilters(CollectionType type) {
        Set<ApiInfoKey> apiSet = new HashSet<>(returnApis());
        List<Bson> apiFilters = new ArrayList<>();
        if (apiSet != null && !apiSet.isEmpty()) {
            for (ApiInfoKey api : apiSet) {
                apiFilters.add(createApiFilters(type, api));
            }
            return Filters.or(apiFilters);
        }

        return MCollection.noMatchFilter;
    } 
}
