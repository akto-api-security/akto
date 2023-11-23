package com.akto.runtime.merge;

import java.util.*;

import com.akto.dao.*;
import com.akto.dto.*;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.Key;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.types.CappedSet;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

public class MergeSimilarUrls {
    
    public static void mergeSTI(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method) {
        Bson stiFilter = Filters.and(
                Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId),
                Filters.eq(SingleTypeInfo._METHOD, method),
                Filters.in(SingleTypeInfo._URL,toMergeUrls) 
        );
        List<SingleTypeInfo> singleTypeInfos = SingleTypeInfoDao.instance.findAll(stiFilter);
        if (singleTypeInfos.isEmpty()) return;

        Set<String> singleTypeInfoSet = new HashSet<>();
        List<SingleTypeInfo> stiResult = new ArrayList<>();
        for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
            singleTypeInfo.setUrl(mergedUrl);
            String key = singleTypeInfo.composeKey();
            if (singleTypeInfoSet.contains(key)) continue;
            singleTypeInfoSet.add(singleTypeInfo.composeKey());
            stiResult.add(singleTypeInfo);
        }

        ArrayList<WriteModel<SingleTypeInfo>> bulkUpdates = new ArrayList<>();
        for (SingleTypeInfo singleTypeInfo: stiResult) {
            Bson filter = SingleTypeInfoDao.createFilters(singleTypeInfo);
            Bson update = Updates.set("count", 1);
            update = Updates.combine(update,
            Updates.setOnInsert(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(singleTypeInfo.getApiCollectionId())));

            bulkUpdates.add(new UpdateOneModel<>(filter, update, new UpdateOptions().upsert(true)));
        }

        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdates);
        SingleTypeInfoDao.instance.deleteAll(stiFilter);
    }

    public static void mergeApiInfo(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method) {
        Bson apiInfoFilter = Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.eq("_id.method", method.name()),
                Filters.in("_id.url", new ArrayList<>(toMergeUrls))
        );
        List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(apiInfoFilter);
        if (apiInfos.isEmpty()) return;
        ApiInfo mainApiInfo = apiInfos.get(0);
        mainApiInfo.setId(new ApiInfo.ApiInfoKey(apiCollectionId, mergedUrl, method));
        ApiInfoDao.instance.insertOne(mainApiInfo);
        ApiInfoDao.instance.deleteAll(apiInfoFilter);
    }

    public static void mergeSampleData(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method){
        Bson sampleDataFilter = Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.eq("_id.method", method.name()),
                Filters.in("_id.url", new ArrayList<>(toMergeUrls))
        );
        List<SampleData> sampleDataList = SampleDataDao.instance.findAll(sampleDataFilter);
        if (sampleDataList.isEmpty()) return;
        SampleData mainSampleData = sampleDataList.get(0);
        mainSampleData.setId(new Key(apiCollectionId, mergedUrl,method, -1, 0, 0));
        SampleDataDao.instance.insertOne(mainSampleData);
        SampleDataDao.instance.deleteAll(sampleDataFilter);
    }

    public static void mergeTrafficInfo(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method) {
        Bson trafficFilter = Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.eq("_id.method", method.name()),
                Filters.in("_id.url", new ArrayList<>(toMergeUrls))
        );
        TrafficInfoDao.instance.deleteAll(trafficFilter);
    }

    public static void mergeSensitiveSampleData(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method) {
        Bson sensitiveSampleDataFilter = Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.eq("_id.method", method.name()),
                Filters.in("_id.url", new ArrayList<>(toMergeUrls))
        );
        List<SensitiveSampleData> sensitiveSampleList = SensitiveSampleDataDao.instance.findAll(sensitiveSampleDataFilter);
        if (sensitiveSampleList.isEmpty()) return;
        Set<String> sensitiveSetSampleDataKeySet = new HashSet<>();
        List<SensitiveSampleData> resultSensitiveSampleData = new ArrayList<>();
        for (SensitiveSampleData sensitiveSampleData: sensitiveSampleList) {
            SingleTypeInfo.ParamId paramId = sensitiveSampleData.getId();
            paramId.setUrl(mergedUrl);
            SingleTypeInfo dummy = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0, 0, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
            String key = dummy.composeKey();
            if (sensitiveSetSampleDataKeySet.contains(key)) continue;
            sensitiveSetSampleDataKeySet.add(key);
            resultSensitiveSampleData.add(sensitiveSampleData);
        }
        SensitiveSampleDataDao.instance.insertMany(resultSensitiveSampleData);
        SensitiveSampleDataDao.instance.deleteAll(sensitiveSampleDataFilter);
    }

    public static void mergeSensitiveParamInfo(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method) {
        Bson sensitiveParamInfoFilter = Filters.and(
                Filters.eq("apiCollectionId", apiCollectionId),
                Filters.eq("method", method),
                Filters.in("url",toMergeUrls)
        );
        List<SensitiveParamInfo> sensitiveParamInfoList = SensitiveParamInfoDao.instance.findAll(sensitiveParamInfoFilter);
        if (sensitiveParamInfoList.isEmpty()) return;
        Set<String> sensitiveParamInfoKeySet = new HashSet<>();
        List<SensitiveParamInfo> resultSensitiveParamInfo = new ArrayList<>();
        for (SensitiveParamInfo sensitiveParamInfo: sensitiveParamInfoList) {
            sensitiveParamInfo.setUrl(mergedUrl);
            SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(sensitiveParamInfo.getParam(), sensitiveParamInfo.getMethod(), sensitiveParamInfo.getResponseCode(), sensitiveParamInfo.isIsHeader(), sensitiveParamInfo.getParam(), SingleTypeInfo.GENERIC, apiCollectionId, false);
            SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0, 0, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
            String key = singleTypeInfo.composeKey();

            if (sensitiveParamInfoKeySet.contains(key)) continue;
            sensitiveParamInfoKeySet.add(key);
            resultSensitiveParamInfo.add(sensitiveParamInfo);
        }
        SensitiveParamInfoDao.instance.insertMany(resultSensitiveParamInfo);
        SensitiveParamInfoDao.instance.deleteAll(sensitiveParamInfoFilter);
    }

    public static void mergeFilterSampleData(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method) {
        Bson filterSampleDataFilter = Filters.and(
                Filters.eq("_id.apiInfoKey.apiCollectionId", apiCollectionId),
                Filters.eq("_id.apiInfoKey.method",method),
                Filters.in("_id.apiInfoKey.url", toMergeUrls)
        );
        List<FilterSampleData> filterSampleList = FilterSampleDataDao.instance.findAll(filterSampleDataFilter);
        if (filterSampleList.isEmpty()) return;
        Set<String> filterSampleDataKeySet = new HashSet<>();
        List<FilterSampleData> resultFilterSampleList = new ArrayList<>();
        for (FilterSampleData filterSampleData: filterSampleList) {
            FilterSampleData.FilterKey id = filterSampleData.getId();
            ApiInfo.ApiInfoKey apiInfoKey = id.apiInfoKey;
            apiInfoKey.setUrl(mergedUrl);
            int filterId = id.filterId;
            String key = StringUtils.joinWith("@", apiInfoKey.getApiCollectionId(), apiInfoKey.url, apiInfoKey.method, filterId);

            if (filterSampleDataKeySet.contains(key)) continue;
            filterSampleDataKeySet.add(key);
            resultFilterSampleList.add(filterSampleData);
        }
        FilterSampleDataDao.instance.insertMany(resultFilterSampleList);
        FilterSampleDataDao.instance.deleteAll(filterSampleDataFilter);
    }


    public static void mergeAndUpdateDb(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method) {
        mergeSTI(mergedUrl, toMergeUrls, apiCollectionId, method);
        mergeApiInfo(mergedUrl, toMergeUrls, apiCollectionId, method);
        mergeSampleData(mergedUrl, toMergeUrls, apiCollectionId, method);
        mergeTrafficInfo(mergedUrl, toMergeUrls, apiCollectionId, method);
        mergeSensitiveSampleData(mergedUrl, toMergeUrls, apiCollectionId, method);
        mergeSensitiveParamInfo(mergedUrl, toMergeUrls, apiCollectionId, method);
        mergeFilterSampleData(mergedUrl, toMergeUrls, apiCollectionId, method);
    }
}
