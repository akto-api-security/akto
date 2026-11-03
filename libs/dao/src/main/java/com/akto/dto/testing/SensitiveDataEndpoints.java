package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;

import com.akto.dao.MCollection;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.testing.custom_groups.DeltaGroupSync;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class SensitiveDataEndpoints extends TestingEndpoints {

    public static final int LIMIT = 1000;

    public SensitiveDataEndpoints() {
        super(Type.SENSITIVE_DATA);
    }

    public SensitiveDataEndpoints(Operator operator) {
        super(Type.SENSITIVE_DATA, operator);
    }

    @BsonIgnore
    List<ApiInfoKey> urls;

    public List<ApiInfoKey> getUrls() {
        return urls;
    }

    public void setUrls(List<ApiInfoKey> urls) {
        this.urls = urls;
    }

    @Override
    public List<ApiInfoKey> returnApis() {
        return urls;
    }

    public final static int API_GROUP_ID = 111_111_999;

    /**
     * The group holds the apis that have sensitive data in a request or response, or that a user marked sensitive.
     * Sensitive params are read from single_type_info by timestamp, so only the ones seen since the last sync are looked at.
     * Apis that stop being sensitive are not found this way and keep the tag.
     */
    private static final DeltaGroupSync GROUP_SYNC = new DeltaGroupSync() {
        @Override
        protected int groupId() {
            return API_GROUP_ID;
        }

        @Override
        protected String groupName() {
            return "sensitive group";
        }

        @Override
        protected void scan(int since, Predicate<Candidate> visitor) {
            SingleTypeInfo.fetchCustomDataTypes(1000000);
            Set<String> sensitiveInRequest = new HashSet<>(SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames());
            Set<String> sensitiveInResponse = new HashSet<>(SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames());
            Set<ApiInfoKey> seen = new HashSet<>();

            // apis a user marked sensitive by hand
            for (SensitiveParamInfo sensitiveParamInfo : SensitiveParamInfoDao.instance.findAll(Filters.eq("sensitive", true))) {
                Method method = Method.fromString(sensitiveParamInfo.getMethod());
                if (method.equals(Method.OTHER)) {
                    continue;
                }
                ApiInfoKey key = new ApiInfoKey(sensitiveParamInfo.getApiCollectionId(), sensitiveParamInfo.getUrl(), method);
                List<Integer> groupIds = sensitiveParamInfo.getCollectionIds();
                if (!report(key, groupIds != null && groupIds.contains(API_GROUP_ID), seen, visitor)) {
                    return;
                }
            }

            // request params are stored with response code -1
            Bson requestFilter = Filters.and(
                Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1),
                Filters.in(SingleTypeInfo.SUB_TYPE, sensitiveInRequest),
                Filters.gte(SingleTypeInfo._TIMESTAMP, since));
            if (!scanSingleTypeInfo(requestFilter, seen, visitor)) {
                return;
            }
            Bson responseFilter = Filters.and(
                Filters.ne(SingleTypeInfo._RESPONSE_CODE, -1),
                Filters.in(SingleTypeInfo.SUB_TYPE, sensitiveInResponse),
                Filters.gte(SingleTypeInfo._TIMESTAMP, since));
            scanSingleTypeInfo(responseFilter, seen, visitor);
        }

        @Override
        protected TestingEndpoints endpointsFor(List<ApiInfoKey> keys) {
            SensitiveDataEndpoints endpoint = new SensitiveDataEndpoints();
            endpoint.setUrls(new ArrayList<>(keys));
            return endpoint;
        }
    };

    public static void updateCollections() {
        GROUP_SYNC.run();
    }

    /** Groups the matching single type infos by api. An api counts as tagged only if all of its matching docs carry the tag. */
    private static boolean scanSingleTypeInfo(Bson filter, Set<ApiInfoKey> seen, Predicate<DeltaGroupSync.Candidate> visitor) {
        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$apiCollectionId")
                .append(SingleTypeInfo._URL, "$url")
                .append(SingleTypeInfo._METHOD, "$method");
        BasicDBObject allTagged = new BasicDBObject("$in", Arrays.<Object>asList(API_GROUP_ID,
                new BasicDBObject("$ifNull", Arrays.<Object>asList("$" + SingleTypeInfo._COLLECTION_IDS, Collections.emptyList()))));
        List<Bson> pipeline = Arrays.asList(
            Aggregates.match(filter),
            Aggregates.group(groupedId, Accumulators.min("allTagged", allTagged)));

        try (MongoCursor<BasicDBObject> cursor = SingleTypeInfoDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).allowDiskUse(true).batchSize(LIMIT).cursor()) {
            while (cursor.hasNext()) {
                BasicDBObject doc = cursor.next();
                BasicDBObject id = (BasicDBObject) doc.get("_id");
                Method method = Method.fromString(id.getString(SingleTypeInfo._METHOD));
                if (method.equals(Method.OTHER)) {
                    continue;
                }
                ApiInfoKey key = new ApiInfoKey(id.getInt(SingleTypeInfo._API_COLLECTION_ID), id.getString(SingleTypeInfo._URL), method);
                if (!report(key, doc.getBoolean("allTagged"), seen, visitor)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** @return false when the scan should stop */
    private static boolean report(ApiInfoKey key, boolean inGroup, Set<ApiInfoKey> seen, Predicate<DeltaGroupSync.Candidate> visitor) {
        if (!seen.add(key)) {
            return true;
        }
        return visitor.test(new DeltaGroupSync.Candidate(key, true, inGroup));
    }

    @Override
    public boolean containsApi(ApiInfoKey key) {
        Bson filterStandardSensitiveParams = SingleTypeInfoDao.instance
                .filterForSensitiveParamsExcludingUserMarkedSensitive(
                        key.getApiCollectionId(), key.getUrl(), key.getMethod().name(), null);
        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(filterStandardSensitiveParams, 0, 1,
                null, Projections.exclude("values"));
        if (list != null && !list.isEmpty()) {
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
