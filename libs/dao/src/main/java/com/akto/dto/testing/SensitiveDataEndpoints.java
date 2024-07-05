package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;

import com.akto.dao.MCollection;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.util.Constants;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class SensitiveDataEndpoints extends TestingEndpoints {

    public static final int LIMIT = 50;

    @BsonIgnore
    int skip = 0;

    @BsonIgnore
    int apiCollectionId = 0;

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

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
        urls = new ArrayList<>();
        if (skip == 0) {
            // User manually set sensitive
            List<SensitiveParamInfo> customSensitiveList = SensitiveParamInfoDao.instance.findAll(
                    Filters.and(
                            Filters.eq("sensitive", true),
                            Filters.eq("apiCollectionId", apiCollectionId)));
            for (SensitiveParamInfo sensitiveParamInfo : customSensitiveList) {
                urls.add(new ApiInfoKey(sensitiveParamInfo.getApiCollectionId(), sensitiveParamInfo.getUrl(),
                        Method.valueOf(sensitiveParamInfo.getMethod())));
            }
        }
        urls.addAll(SingleTypeInfoDao.instance.fetchSensitiveEndpoints(apiCollectionId, skip, LIMIT));
        return urls;
    }

    final static int API_GROUP_ID = 111_111_999;

    public static void updateCollections(){
        ApiCollectionUsers.reset(API_GROUP_ID);

        Set<Integer> responseCodes = SingleTypeInfoDao.instance.findDistinctFields(SingleTypeInfo._RESPONSE_CODE, Integer.class, Filters.exists(SingleTypeInfo._RESPONSE_CODE));
        Set<String> subTypes = SingleTypeInfoDao.instance.findDistinctFields(SingleTypeInfo.SUB_TYPE, String.class, Filters.exists(SingleTypeInfo.SUB_TYPE));

        Set<String> sensitiveInResponse = new HashSet<>(SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames());
        Set<String> sensitiveInRequest = new HashSet<>(SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames());

        for(int responseCode : responseCodes){
            for(String subType : subTypes){

                Bson responseCodeFilter = Filters.eq(SingleTypeInfo._RESPONSE_CODE, responseCode);
                Bson subTypeFilter = Filters.eq(SingleTypeInfo.SUB_TYPE, subType);

                if ((responseCode == -1 && sensitiveInRequest.contains(subType))
                        || responseCode != -1 && sensitiveInResponse.contains(subType)) {

                            int timestamp = Context.now() + Constants.ONE_DAY_TIMESTAMP;

                            boolean hasMore = true;

                            while(hasMore){
                                hasMore = false;

                                

                            }
                            

                }
            }
        }

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

    // public static void main(String[] args) {
    //     DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
    //     Context.accountId.set(1_000_000);

    //     SensitiveDataEndpoints ep = new SensitiveDataEndpoints();
    //     ep.setApiCollectionId(1719900296);
    //     ep.returnApis();

    // }

}
