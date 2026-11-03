package com.akto.dto.testing.custom_groups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.MCollection;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiInfo.AuthType;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class UnauthenticatedEndpoint extends TestingEndpoints {

    private static final int limit = 1000;

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

    /**
     * The group follows api_info (the source of truth for auth types). Auth types only change in runtime upserts, which
     * bump lastSeen, so only the apis seen since the last sync are looked at.
     */
    private static final DeltaGroupSync GROUP_SYNC = new DeltaGroupSync() {
        @Override
        protected int groupId() {
            return UNAUTHENTICATED_GROUP_ID;
        }

        @Override
        protected String groupName() {
            return "unauthenticated group";
        }

        @Override
        protected void scan(int from, int to, Predicate<Candidate> visitor) {
            List<Integer> collectionIds = new ArrayList<>(fetchAllowedCollectionIds());
            for (int i = 0; i < collectionIds.size(); i += limit) {
                List<Integer> chunk = collectionIds.subList(i, Math.min(i + limit, collectionIds.size()));
                Bson filter = Filters.and(
                    Filters.in(ApiInfo.ID_API_COLLECTION_ID, chunk),
                    Filters.gte(ApiInfo.LAST_SEEN, from),
                    Filters.lt(ApiInfo.LAST_SEEN, to)
                );

                try (MongoCursor<ApiInfo> cursor = ApiInfoDao.instance.getMCollection().find(filter)
                        .projection(Projections.include("_id", ApiInfo.ALL_AUTH_TYPES_FOUND, ApiInfo.COLLECTION_IDS))
                        .batchSize(limit).cursor()) {
                    while (cursor.hasNext()) {
                        ApiInfo apiInfo = cursor.next();
                        Set<Set<AuthType>> authTypesFound = apiInfo.getAllAuthTypesFound();
                        boolean isUnauthenticated = authTypesFound != null && authTypesFound.contains(UNAUTHENTICATED_AUTH_SET);
                        List<Integer> apiGroupIds = apiInfo.getCollectionIds();
                        boolean inGroup = apiGroupIds != null && apiGroupIds.contains(UNAUTHENTICATED_GROUP_ID);
                        if (!visitor.test(new Candidate(apiInfo.getId(), isUnauthenticated, inGroup))) {
                            return;
                        }
                    }
                }
            }
        }

        @Override
        protected TestingEndpoints endpointsFor(List<ApiInfoKey> keys) {
            UnauthenticatedEndpoint endpoint = new UnauthenticatedEndpoint();
            endpoint.setApiInfos(new ArrayList<>(keys));
            return endpoint;
        }
    };

    public static void updateCollections(){
        GROUP_SYNC.run();
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
