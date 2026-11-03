package com.akto.dto.testing;

import java.util.ArrayList;
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
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class SensitiveDataEndpoints extends TestingEndpoints {


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
     * Sensitive params are read from single_type_info by timestamp, so only the ones seen since the last sync are looked at;
     * the ones a user marked by hand have no time and are checked on every run.
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
        protected void scanUntimed(Predicate<Candidate> visitor) {
            // apis a user marked sensitive by hand
            for (SensitiveParamInfo sensitiveParamInfo : SensitiveParamInfoDao.instance.findAll(Filters.eq("sensitive", true))) {
                Method method = Method.fromString(sensitiveParamInfo.getMethod());
                if (method.equals(Method.OTHER)) {
                    continue;
                }
                ApiInfoKey key = new ApiInfoKey(sensitiveParamInfo.getApiCollectionId(), sensitiveParamInfo.getUrl(), method);
                List<Integer> groupIds = sensitiveParamInfo.getCollectionIds();
                if (!visitor.test(new Candidate(key, true, groupIds != null && groupIds.contains(API_GROUP_ID)))) {
                    return;
                }
            }
        }

        @Override
        protected void scan(int from, int to, Predicate<Candidate> visitor) {
            SingleTypeInfo.fetchCustomDataTypes(1000000);
            Set<String> sensitiveInRequest = new HashSet<>(SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames());
            Set<String> sensitiveInResponse = new HashSet<>(SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames());
            Bson timeRange = Filters.and(Filters.gte(SingleTypeInfo._TIMESTAMP, from), Filters.lt(SingleTypeInfo._TIMESTAMP, to));

            // request params are stored with response code -1
            Bson requestFilter = Filters.and(
                Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1),
                Filters.in(SingleTypeInfo.SUB_TYPE, sensitiveInRequest),
                timeRange);
            if (!scanSingleTypeInfo(requestFilter, visitor)) {
                return;
            }
            Bson responseFilter = Filters.and(
                Filters.ne(SingleTypeInfo._RESPONSE_CODE, -1),
                Filters.in(SingleTypeInfo.SUB_TYPE, sensitiveInResponse),
                timeRange);
            scanSingleTypeInfo(responseFilter, visitor);
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
