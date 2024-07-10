package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.MCollection;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLMethods.Method;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

public class SensitiveDataEndpoints extends TestingEndpoints {

    public static final int LIMIT = 1000;
    private static final Logger logger = LoggerFactory.getLogger(SensitiveDataEndpoints.class);

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

    public static void updateCollections() {
        ApiCollectionUsers.reset(API_GROUP_ID);
        SingleTypeInfo.fetchCustomDataTypes(1000000);
        Set<Integer> responseCodes = SingleTypeInfoDao.instance.findDistinctFields(SingleTypeInfo._RESPONSE_CODE,
                Integer.class, Filters.exists(SingleTypeInfo._RESPONSE_CODE));
        Set<String> subTypes = SingleTypeInfoDao.instance.findDistinctFields(SingleTypeInfo.SUB_TYPE, String.class,
                Filters.exists(SingleTypeInfo.SUB_TYPE));

        logger.info(String.format("acc: %d found response codes: %s", Context.accountId.get(), responseCodes.toString()));
        logger.info(String.format("acc: %d found subTypes: %s", Context.accountId.get(), subTypes.toString()));

        Set<String> sensitiveInResponse = new HashSet<>(SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames());
        Set<String> sensitiveInRequest = new HashSet<>(SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames());

        logger.info(String.format("acc: %d Using sensitive in req: %s", Context.accountId.get(), sensitiveInRequest.toString()));
        logger.info(String.format("acc: %d Using sensitive in res: %s", Context.accountId.get(), sensitiveInResponse.toString()));

        List<ApiInfoKey> localUrls = new ArrayList<>();
        List<SensitiveParamInfo> customSensitiveList = SensitiveParamInfoDao.instance.findAll(
                Filters.eq("sensitive", true));
        for (SensitiveParamInfo sensitiveParamInfo : customSensitiveList) {
            localUrls.add(new ApiInfoKey(sensitiveParamInfo.getApiCollectionId(), sensitiveParamInfo.getUrl(),
                    Method.valueOf(sensitiveParamInfo.getMethod())));
        }

        SensitiveDataEndpoints sensitiveDataEndpoints = new SensitiveDataEndpoints();
        if (localUrls != null && !localUrls.isEmpty()) {
            sensitiveDataEndpoints.setUrls(new ArrayList<>(localUrls));
            ApiCollectionUsers.addToCollectionsForCollectionId(
                    Collections.singletonList(sensitiveDataEndpoints), API_GROUP_ID);
        }

        final String LAST_TIMESTAMP = "lastTimestamp";

        for (int responseCode : responseCodes) {
            for (String subType : subTypes) {

                Bson responseCodeFilter = Filters.eq(SingleTypeInfo._RESPONSE_CODE, responseCode);
                Bson subTypeFilter = Filters.eq(SingleTypeInfo.SUB_TYPE, subType);

                if ((responseCode == -1 && sensitiveInRequest.contains(subType))
                        || (responseCode != -1 && sensitiveInResponse.contains(subType))) {

                    int timestamp = Context.now() + Constants.ONE_DAY_TIMESTAMP;

                    logger.info(String.format("AccountId: %d Starting update sensitive data collection for %d %s",
                            Context.accountId.get(), responseCode, subType));
                    int skip = 0;
                    while (true) {
                        List<Bson> pipeline = new ArrayList<>();
                        pipeline.add(Aggregates.sort(Sorts.descending(SingleTypeInfo._TIMESTAMP)));
                        pipeline.add(Aggregates.match(
                                Filters.and(
                                        responseCodeFilter, subTypeFilter,
                                        Filters.lt(SingleTypeInfo._TIMESTAMP, timestamp))));
                        pipeline.add(Aggregates.skip(skip));
                        pipeline.add(Aggregates.limit(LIMIT));
                        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID,
                                "$apiCollectionId")
                                .append(SingleTypeInfo._URL, "$url")
                                .append(SingleTypeInfo._METHOD, "$method");

                        Bson projections = Projections.fields(
                                Projections.include(SingleTypeInfo._TIMESTAMP,
                                        SingleTypeInfo._API_COLLECTION_ID, SingleTypeInfo._URL,
                                        SingleTypeInfo._METHOD));

                        pipeline.add(Aggregates.project(projections));
                        pipeline.add(Aggregates.group(groupedId,
                                Accumulators.last(LAST_TIMESTAMP, "$timestamp")));
                                
                        MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection()
                                .aggregate(pipeline, BasicDBObject.class).cursor();

                        Set<ApiInfo.ApiInfoKey> endpoints = new HashSet<>();
                        while (endpointsCursor.hasNext()) {
                            BasicDBObject v = endpointsCursor.next();
                            try {
                                BasicDBObject vv = (BasicDBObject) v.get("_id");
                                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(
                                        (int) vv.get("apiCollectionId"),
                                        (String) vv.get("url"),
                                        URLMethods.Method.fromString((String) vv.get("method")));
                                endpoints.add(apiInfoKey);
                                int localTimestamp = v.getInt(LAST_TIMESTAMP);
                                timestamp = Math.min(timestamp, localTimestamp);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        if (!endpoints.isEmpty()) {
                            List<ApiInfoKey> endpointList = new ArrayList<>();
                            for (ApiInfoKey apiInfoKey : endpoints) {
                                // skip unknown methods
                                if (!apiInfoKey.getMethod().equals(Method.OTHER)) {
                                    endpointList.add(apiInfoKey);
                                }
                            }

                            if (!endpointList.isEmpty()) {
                                logger.info(String.format(
                                        "AccountId: %d Running update sensitive data collection for %d %s with endpoints %d skip %d",
                                        Context.accountId.get(), responseCode, subType, endpointList.size(), skip));
                                sensitiveDataEndpoints.setUrls(endpointList);
                                ApiCollectionUsers.addToCollectionsForCollectionId(
                                        Collections.singletonList(sensitiveDataEndpoints), API_GROUP_ID);
                            }
                            timestamp = timestamp + 1;
                            skip += LIMIT;
                            continue;
                        }
                        logger.info(String.format("AccountId: %d Finished update sensitive data collection for %d %s",
                                Context.accountId.get(), responseCode, subType));
                        break;
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

}
