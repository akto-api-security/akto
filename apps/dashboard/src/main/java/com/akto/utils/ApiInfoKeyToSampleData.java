package com.akto.utils;

import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.SampleData;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

public class ApiInfoKeyToSampleData {
    public static List<SampleData> convertApiInfoKeyToSampleData(List<ApiInfo.ApiInfoKey> apiInfoKeyList) {
        List<Integer> apiCollectionIds = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        List<String> methods = new ArrayList<>();

        for (ApiInfo.ApiInfoKey apiInfoKey : apiInfoKeyList) {
            apiCollectionIds.add(apiInfoKey.getApiCollectionId());
            urls.add(apiInfoKey.getUrl());
            methods.add(apiInfoKey.getMethod().name());
        }

        Bson filter = Filters.and(
                Filters.in("_id.apiCollectionId", apiCollectionIds),
                Filters.in("_id.url", urls),
                Filters.in("_id.method", methods)
        );

        return SampleDataDao.instance.findAll(filter);
    }
}
