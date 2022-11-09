package com.akto.runtime;

import com.akto.MongoBasedTest;
import com.akto.dao.*;
import com.akto.dto.ApiInfo;
import com.akto.dto.FilterSampleData;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.type.*;
import com.akto.types.CappedSet;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class TestApiCatalogSync extends MongoBasedTest {

    @Test
    public void testFillUrlParams() {
        RequestTemplate requestTemplate1 = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder());
        validateSubTypeAndMinMax(requestTemplate1, "/api/books/378282246310005", "/api/books/STRING",
                SingleTypeInfo.CREDIT_CARD, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE, 1,1);
        validateSubTypeAndMinMax(requestTemplate1, "api/books/378282246310005", "/api/books/STRING",
                SingleTypeInfo.CREDIT_CARD, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE, 2, 1);
        validateSubTypeAndMinMax(requestTemplate1, "api/books/4111111111111111/", "/api/books/STRING",
                SingleTypeInfo.CREDIT_CARD, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE, 3, 2);

        RequestTemplate requestTemplate2 = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder());
        validateSubTypeAndMinMax(requestTemplate2, "/api/books/234", "/api/books/INTEGER",
                SingleTypeInfo.INTEGER_32, 234, 234, 1, 1);
        validateSubTypeAndMinMax(requestTemplate2, "api/books/999", "/api/books/INTEGER",
                SingleTypeInfo.INTEGER_32, 234,999, 2, 2);
    }

    private void validateSubTypeAndMinMax(RequestTemplate requestTemplate, String url, String templateUrl,
                                          SingleTypeInfo.SubType subType, long minValue, long maxValue, int count,
                                          int valuesCount) {

        String[] tokenizedUrl = APICatalogSync.tokenize(url);
        URLTemplate urlTemplate = APICatalogSync.createUrlTemplate(templateUrl, URLMethods.Method.GET);

        requestTemplate.fillUrlParams(tokenizedUrl, urlTemplate, 0);

        assertEquals(1, requestTemplate.getUrlParams().size());
        KeyTypes keyTypes = requestTemplate.getUrlParams().get(2);
        SingleTypeInfo singleTypeInfo= keyTypes.getOccurrences().get(subType);
        assertEquals(subType, singleTypeInfo.getSubType());
        assertEquals(maxValue, singleTypeInfo.getMaxValue());
        assertEquals(minValue, singleTypeInfo.getMinValue());
        assertEquals(count, singleTypeInfo.getCount());
        assertEquals(valuesCount, singleTypeInfo.getValues().getElements().size());
    }


    @Test
    public void testMergeAndUpdateDb() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();
        TrafficInfoDao.instance.getMCollection().drop();
        SensitiveSampleDataDao.instance.getMCollection().drop();
        SensitiveParamInfoDao.instance.getMCollection().drop();
        FilterSampleDataDao.instance.getMCollection().drop();

        Set<String> toMergeUrls = new HashSet<>();
        toMergeUrls.add("/api/books/1");
        toMergeUrls.add("/api/books/2");
        toMergeUrls.add("/api/books/3");
        String mergedUrl = "/api/books/INTEGER";

        for (Object m: toMergeUrls.toArray()) {
            buildAndInsert((String) m);
        }

        APICatalogSync.mergeAndUpdateDb(mergedUrl, toMergeUrls, 100, URLMethods.Method.GET);

        List<SingleTypeInfo> singleTypeInfos = SingleTypeInfoDao.instance.findAll(new BasicDBObject());
        for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
            assertEquals(mergedUrl, singleTypeInfo.getUrl());
        }
        assertEquals(12, singleTypeInfos.size());

        List<ApiInfo> apiInfoList = ApiInfoDao.instance.findAll(new BasicDBObject());
        assertEquals(1, apiInfoList.size());
        assertEquals(mergedUrl, apiInfoList.get(0).getId().url);

        List<SampleData> sampleDataList = SampleDataDao.instance.findAll(new BasicDBObject());
        assertEquals(1, sampleDataList.size());
        assertEquals(mergedUrl, sampleDataList.get(0).getId().url);

        long trafficInfoCount = TrafficInfoDao.instance.getMCollection().countDocuments();
        assertEquals(0, trafficInfoCount);

        List<SensitiveSampleData> sensitiveSampleDataList = SensitiveSampleDataDao.instance.findAll(new BasicDBObject());
        for (SensitiveSampleData sensitiveSampleData: sensitiveSampleDataList) {
            assertEquals(mergedUrl, sensitiveSampleData.getId().getUrl());
        }
        assertEquals(12, sensitiveSampleDataList.size());

        List<SensitiveParamInfo> sensitiveParamInfoList = SensitiveParamInfoDao.instance.findAll(new BasicDBObject());
        for (SensitiveParamInfo sensitiveParamInfo: sensitiveParamInfoList) {
            assertEquals(mergedUrl, sensitiveParamInfo.getUrl());
        }
        assertEquals(12, sensitiveParamInfoList.size());
    }

    public void buildAndInsert(String url) {
        List<String> params = new ArrayList<>();
        for (int i = 0; i<4; i++) {
            params.add("param_" + url + "_"+i);
        }
        int apiCollectionId = 100;
        URLMethods.Method method = URLMethods.Method.GET;
        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();
        for (String param: params) {
            SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, method.name(), 200, false, param, SingleTypeInfo.GENERIC, apiCollectionId, false);
            SingleTypeInfo sti = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0, 0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
            singleTypeInfos.add(sti);
        }
        SingleTypeInfoDao.instance.insertMany(singleTypeInfos);

        ApiInfo apiInfo = new ApiInfo(apiCollectionId, url, method);
        ApiInfoDao.instance.insertOne(apiInfo);

        SampleDataDao.instance.insertOne(
                new SampleData(
                        new Key(apiCollectionId, url, method, -1, 0,0),
                        Collections.singletonList("")
                )
        );

        List<TrafficInfo> trafficInfoList = new ArrayList<>();
        for (int i =0; i<10; i++) {
            Key key = new Key(apiCollectionId, url, method, -1, i*10,i*10+10);
            TrafficInfo trafficInfo = new TrafficInfo(key, new HashMap<>());
            trafficInfoList.add(trafficInfo);
        }

        TrafficInfoDao.instance.insertMany(trafficInfoList);


        List<SensitiveSampleData> sensitiveSampleDataList = new ArrayList<>();
        for (String param: params) {
            SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, method.name(), 200, false, param, SingleTypeInfo.GENERIC, apiCollectionId, false);
            SensitiveSampleData sensitiveSampleData = new SensitiveSampleData(
                    paramId, Collections.singletonList("1")
            );
            sensitiveSampleDataList.add(sensitiveSampleData);
        }
        SensitiveSampleDataDao.instance.insertMany(sensitiveSampleDataList);


        List<SensitiveParamInfo> sensitiveParamInfos = new ArrayList<>();
        for (String param: params) {
            SensitiveParamInfo sensitiveParamInfo = new SensitiveParamInfo(
                    url, method.name(), 200, false, param, apiCollectionId, true
            );

            sensitiveParamInfos.add(sensitiveParamInfo);
        }
        SensitiveParamInfoDao.instance.insertMany(sensitiveParamInfos);

        List<FilterSampleData> filterSampleDataList = new ArrayList<>();
        for (int i =0;i<3; i++) {
            FilterSampleData filterSampleData = new FilterSampleData(apiInfo.getId(), i);
            filterSampleDataList.add(filterSampleData);
        }
        FilterSampleDataDao.instance.insertMany(filterSampleDataList);


    }
}
