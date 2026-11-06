package com.akto.dto.testing.custom_groups;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.MCollection;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiInfo.AuthType;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
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

    private static final String UNAUTHENTICATED_AUTH_NAME = AuthType.UNAUTHENTICATED.name();

    /**
     * An api is unauthenticated when one of the auth type sets found for it is only UNAUTHENTICATED. The sets are read as
     * plain names, not as the AuthType enum, so a name this code does not know (x-api-key) cannot fail the read.
     */
    private static boolean isUnauthenticated(Object allAuthTypesFound) {
        if (!(allAuthTypesFound instanceof List)) {
            return false;
        }
        for (Object authTypes : (List<?>) allAuthTypesFound) {
            if (authTypes instanceof List && ((List<?>) authTypes).size() == 1
                    && UNAUTHENTICATED_AUTH_NAME.equals(((List<?>) authTypes).get(0))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The group follows api_info (the source of truth for auth types). Auth types only change in runtime upserts, which
     * bump lastSeen, so only the apis seen since the last sync are looked at.
     */
    private static final ApiGroupSync GROUP_SYNC = new ApiGroupSync() {
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
            List<Integer> collectionIds = new ArrayList<>(fetchApiCollectionIds());
            for (int i = 0; i < collectionIds.size(); i += limit) {
                List<Integer> chunk = collectionIds.subList(i, Math.min(i + limit, collectionIds.size()));
                Bson filter = Filters.and(
                    Filters.in(ApiInfo.ID_API_COLLECTION_ID, chunk),
                    Filters.gte(ApiInfo.LAST_SEEN, from),
                    Filters.lt(ApiInfo.LAST_SEEN, to)
                );

                try (MongoCursor<Document> cursor = ApiInfoDao.instance.getMCollection().withDocumentClass(Document.class).find(filter)
                        .projection(Projections.include("_id", ApiInfo.ALL_AUTH_TYPES_FOUND, ApiInfo.COLLECTION_IDS, ApiInfo.LAST_SEEN))
                        .batchSize(limit).cursor()) {
                    while (cursor.hasNext()) {
                        Document apiInfo = cursor.next();
                        Document id = apiInfo.get("_id", Document.class);
                        ApiInfoKey key = new ApiInfoKey(((Number) id.get(ApiInfo.ApiInfoKey.API_COLLECTION_ID)).intValue(),
                                id.getString(ApiInfo.ApiInfoKey.URL), Method.valueOf(id.getString(ApiInfo.ApiInfoKey.METHOD)));
                        boolean isUnauthenticated = isUnauthenticated(apiInfo.get(ApiInfo.ALL_AUTH_TYPES_FOUND));
                        List<Integer> apiGroupIds = apiInfo.getList(ApiInfo.COLLECTION_IDS, Integer.class);
                        boolean inGroup = apiGroupIds != null && apiGroupIds.contains(UNAUTHENTICATED_GROUP_ID);
                        Number lastSeen = apiInfo.get(ApiInfo.LAST_SEEN, Number.class);
                        if (!visitor.test(new Candidate(key, isUnauthenticated, inGroup, lastSeen == null ? 0 : lastSeen.intValue()))) {
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
