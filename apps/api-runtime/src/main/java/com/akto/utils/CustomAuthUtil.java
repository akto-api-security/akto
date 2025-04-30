package com.akto.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import org.apache.commons.lang3.function.FailableFunction;
import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.policies.AuthPolicy;
import com.google.gson.Gson;

import static com.akto.dto.ApiInfo.ALL_AUTH_TYPES_FOUND;

public class CustomAuthUtil {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CustomAuthUtil.class, LogDb.DASHBOARD);

    public static Bson getFilters(ApiInfo apiInfo) {
        return Filters.and(
                Filters.eq(SingleTypeInfo._URL, apiInfo.getId().getUrl()),
                Filters.eq(SingleTypeInfo._METHOD, apiInfo.getId().getMethod().name()),
                Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1),
                Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiInfo.getId().getApiCollectionId()));
    }

    private static Set<ApiInfo.AuthType> unauthenticatedTypes = new HashSet<>(Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED));

    final static Gson gson = new Gson();

    private static HttpResponseParams createResponseParamsFromSTI(List<SingleTypeInfo> list) {

        HttpResponseParams responseParams = new HttpResponseParams();
        HttpRequestParams requestParams = new HttpRequestParams();
        Map<String, List<String>> headers = new HashMap<>();
        Map<String, String> payloadKeys = new HashMap<>();
        for(SingleTypeInfo sti: list){
            if(sti.isIsHeader()){
                List<String> values = new ArrayList<>();
                if(sti.getValues()!=null && sti.getValues().getElements()!=null){
                    values = new ArrayList<>(sti.getValues().getElements());
                }
                headers.put(sti.getParam(), values);
            } else if(!sti.getIsUrlParam()) {
                payloadKeys.put(sti.getParam(), "");
            }
        }
        String payloadJsonString = "{}";
        try {
            payloadJsonString = gson.toJson(payloadKeys);
        } catch(Exception e){
            payloadJsonString = "{}";
        }
        requestParams.setHeaders(headers);
        requestParams.setPayload(payloadJsonString);
        responseParams.requestParams = requestParams;
        return responseParams;
    }

    public static void customAuthTypeUtil(List<CustomAuthType> customAuthTypes) {

        FailableFunction<ApiInfo, UpdateOneModel<ApiInfo>, Exception> func = (apiInfo) -> {

            Set<Set<ApiInfo.AuthType>> authTypes = apiInfo.getAllAuthTypesFound();
            authTypes.remove(new HashSet<>());
            authTypes.remove(unauthenticatedTypes);

            SampleData sampleData = SampleDataDao.instance.fetchAllSampleDataForApi(
                    apiInfo.getId().getApiCollectionId(),
                    apiInfo.getId().getUrl(), apiInfo.getId().getMethod());
            boolean sampleProcessed = false;
            if (sampleData != null && sampleData.getSamples() != null && !sampleData.getSamples().isEmpty()) {
                for (String sample : sampleData.getSamples()) {
                    try {
                        HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
                        AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
                        sampleProcessed = true;
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Unable to parse sample data for custom auth setup job");
                    }
                }
            }
                
            if (!sampleProcessed) {
                List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(getFilters(apiInfo));
                try {
                    HttpResponseParams httpResponseParams = createResponseParamsFromSTI(list);
                    AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Unable to parse STIs for custom auth setup job");
                }
            }

                return new UpdateOneModel<>(
                    ApiInfoDao.getFilter(apiInfo.getId()),
                    Updates.set(ALL_AUTH_TYPES_FOUND, apiInfo.getAllAuthTypesFound()),
                    new UpdateOptions().upsert(false));
        };

        CalculateJob.apiInfoUpdateJob(func);
    }

    public static void resetAllCustomAuthTypes() {

        /*
         * 1. remove custom auth type from all entries. 
         * 2. remove unauthenticated auth type from all entries since on reset,
         * auth type should be calculated again.
         */
        ApiInfoDao.instance.updateMany(new BasicDBObject(),
                Updates.pull(ALL_AUTH_TYPES_FOUND + ".$[]", new BasicDBObject().append("$in",
                        new String[] { ApiInfo.AuthType.CUSTOM.name(), ApiInfo.AuthType.UNAUTHENTICATED.name() })));
    }
}
