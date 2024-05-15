package com.akto.runtime;

import com.akto.MongoBasedTest;
import com.akto.dao.*;
import com.akto.dto.AktoDataType;
import com.akto.dto.ApiInfo;
import com.akto.dto.FilterSampleData;
import com.akto.dto.IgnoreData;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.type.*;
import com.akto.types.CappedSet;
import com.mongodb.BasicDBObject;

import com.mongodb.client.model.Updates;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestApiCatalogSync extends MongoBasedTest {
    public void testInitializer() {
        Map<String, AktoDataType> aktoDataTypeMap = new HashMap<>();
        aktoDataTypeMap.put("JWT", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("PHONE_NUMBER", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("CREDIT_CARD", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("IP_ADDRESS", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("EMAIL", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("SSN", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("UUID", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        AccountDataTypesInfo info = SingleTypeInfo.getAccountToDataTypesInfo().get(ACCOUNT_ID);
        if (info == null) {
            info = new AccountDataTypesInfo();
        }
        info.setAktoDataTypeMap(aktoDataTypeMap);
        SingleTypeInfo.getAccountToDataTypesInfo().put(ACCOUNT_ID, info);
    }

    @Test
    public void testFillUrlParams() {
        testInitializer();
        RequestTemplate requestTemplate1 = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder());
        validateSubTypeAndMinMax(requestTemplate1, "/api/books/378282246310005", "/api/books/STRING",
                SingleTypeInfo.CREDIT_CARD, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE, 1, 1);
        validateSubTypeAndMinMax(requestTemplate1, "api/books/378282246310005", "/api/books/STRING",
                SingleTypeInfo.CREDIT_CARD, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE, 2, 1);
        validateSubTypeAndMinMax(requestTemplate1, "api/books/4111111111111111/", "/api/books/STRING",
                SingleTypeInfo.CREDIT_CARD, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE, 3, 2);

        RequestTemplate requestTemplate2 = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder());
        validateSubTypeAndMinMax(requestTemplate2, "/api/books/234", "/api/books/INTEGER",
                SingleTypeInfo.INTEGER_32, 234, 234, 1, 1);
        validateSubTypeAndMinMax(requestTemplate2, "api/books/999", "/api/books/INTEGER",
                SingleTypeInfo.INTEGER_32, 234, 999, 2, 2);
    }

    private void validateSubTypeAndMinMax(RequestTemplate requestTemplate, String url, String templateUrl,
                                          SingleTypeInfo.SubType subType, long minValue, long maxValue, int count,
                                          int valuesCount) {

        String[] tokenizedUrl = APICatalogSync.tokenize(url);
        URLTemplate urlTemplate = APICatalogSync.createUrlTemplate(templateUrl, URLMethods.Method.GET);

        requestTemplate.fillUrlParams(tokenizedUrl, urlTemplate, 0);

        assertEquals(1, requestTemplate.getUrlParams().size());
        KeyTypes keyTypes = requestTemplate.getUrlParams().get(2);
        SingleTypeInfo singleTypeInfo = keyTypes.getOccurrences().get(subType);
        assertEquals(subType, singleTypeInfo.getSubType());
        assertEquals(maxValue, singleTypeInfo.getMaxValue());
        assertEquals(minValue, singleTypeInfo.getMinValue());
        assertEquals(count, singleTypeInfo.getCount());
        assertEquals(valuesCount, singleTypeInfo.getValues().getElements().size());
    }

    public void buildAndInsert(String url) {
        List<String> params = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            params.add("param_" + url + "_" + i);
        }
        int apiCollectionId = 100;
        URLMethods.Method method = URLMethods.Method.GET;
        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();
        for (String param : params) {
            SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, method.name(), 200, false, param, SingleTypeInfo.GENERIC, apiCollectionId, false);
            SingleTypeInfo sti = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0, 0, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
            singleTypeInfos.add(sti);
        }
        SingleTypeInfoDao.instance.insertMany(singleTypeInfos);

        ApiInfo apiInfo = new ApiInfo(apiCollectionId, url, method);
        ApiInfoDao.instance.insertOne(apiInfo);

        SampleDataDao.instance.insertOne(
                new SampleData(
                        new Key(apiCollectionId, url, method, -1, 0, 0),
                        Collections.singletonList("")
                )
        );

        List<TrafficInfo> trafficInfoList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Key key = new Key(apiCollectionId, url, method, -1, i * 10, i * 10 + 10);
            TrafficInfo trafficInfo = new TrafficInfo(key, new HashMap<>());
            trafficInfoList.add(trafficInfo);
        }

        TrafficInfoDao.instance.insertMany(trafficInfoList);


        List<SensitiveSampleData> sensitiveSampleDataList = new ArrayList<>();
        for (String param : params) {
            SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, method.name(), 200, false, param, SingleTypeInfo.GENERIC, apiCollectionId, false);
            SensitiveSampleData sensitiveSampleData = new SensitiveSampleData(
                    paramId, Collections.singletonList("1")
            );
            sensitiveSampleDataList.add(sensitiveSampleData);
        }
        SensitiveSampleDataDao.instance.insertMany(sensitiveSampleDataList);


        List<SensitiveParamInfo> sensitiveParamInfos = new ArrayList<>();
        for (String param : params) {
            SensitiveParamInfo sensitiveParamInfo = new SensitiveParamInfo(
                    url, method.name(), 200, false, param, apiCollectionId, true
            );

            sensitiveParamInfos.add(sensitiveParamInfo);
        }
        SensitiveParamInfoDao.instance.insertMany(sensitiveParamInfos);

        List<FilterSampleData> filterSampleDataList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            FilterSampleData filterSampleData = new FilterSampleData(apiInfo.getId(), i);
            filterSampleDataList.add(filterSampleData);
        }
        FilterSampleDataDao.instance.insertMany(filterSampleDataList);


    }

    @Test
    public void testClearValuesInDB() {
        SingleTypeInfoDao.instance.getMCollection().drop();

        int stiValueLimit = SingleTypeInfo.VALUES_LIMIT;

        SingleTypeInfo.ParamId paramId1 = new SingleTypeInfo.ParamId("/api/books", "GET", 200, false, "param1", SingleTypeInfo.GENERIC, 0, false);
        SingleTypeInfo sti1 = new SingleTypeInfo(paramId1, new HashSet<>(), new HashSet<>(), 0, 0, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        SingleTypeInfoDao.instance.insertOne(sti1);
        SingleTypeInfoDao.instance.updateOne(SingleTypeInfoDao.createFilters(sti1), Updates.pushEach(SingleTypeInfo._VALUES+".elements", generateRandomValuesSet(stiValueLimit+10)));


        SingleTypeInfo.ParamId paramId2 = new SingleTypeInfo.ParamId("/api/cars", "GET", 200, false, "param1", SingleTypeInfo.GENERIC, 0, false);
        SingleTypeInfo sti2 = new SingleTypeInfo(paramId2, new HashSet<>(), new HashSet<>(), 0, 0, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        SingleTypeInfoDao.instance.insertOne(sti2);
        SingleTypeInfoDao.instance.updateOne(SingleTypeInfoDao.createFilters(sti2), Updates.pushEach(SingleTypeInfo._VALUES+".elements", generateRandomValuesSet(stiValueLimit-10)));

        SingleTypeInfo.ParamId paramId3 = new SingleTypeInfo.ParamId("/api/toys", "GET", 200, false, "param1", SingleTypeInfo.GENERIC, 0, false);
        SingleTypeInfo sti3 = new SingleTypeInfo(paramId3, new HashSet<>(), new HashSet<>(), 0, 0, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        SingleTypeInfoDao.instance.insertOne(sti3);
        SingleTypeInfoDao.instance.updateOne(SingleTypeInfoDao.createFilters(sti3), Updates.pushEach(SingleTypeInfo._VALUES+".elements", generateRandomValuesSet(stiValueLimit)));

        SingleTypeInfo.ParamId paramId4 = new SingleTypeInfo.ParamId("/api/chairs", "GET", 200, false, "param1", SingleTypeInfo.INTEGER_32, 0, false);
        SingleTypeInfo sti4 = new SingleTypeInfo(paramId4, new HashSet<>(), new HashSet<>(), 0, 0, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        SingleTypeInfoDao.instance.insertOne(sti4);
        SingleTypeInfoDao.instance.updateOne(SingleTypeInfoDao.createFilters(sti4), Updates.pushEach(SingleTypeInfo._VALUES+".elements", generateRandomValuesSet(stiValueLimit+10)));

        APICatalogSync.clearValuesInDB();

        SingleTypeInfo sti1Db = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(sti1));
        assertEquals(stiValueLimit, sti1Db.getValues().count());
        assertEquals(SingleTypeInfo.Domain.ANY, sti1Db.getDomain());

        SingleTypeInfo sti2Db = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(sti2));
        assertEquals(stiValueLimit-10, sti2Db.getValues().count());
        assertEquals(SingleTypeInfo.Domain.ENUM, sti2Db.getDomain());

        SingleTypeInfo sti3Db = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(sti3));
        assertEquals(stiValueLimit, sti3Db.getValues().count());
        assertEquals(SingleTypeInfo.Domain.ENUM, sti3Db.getDomain());

        SingleTypeInfo sti4Db = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(sti4));
        assertEquals(stiValueLimit, sti4Db.getValues().count());
        assertEquals(SingleTypeInfo.Domain.RANGE, sti4Db.getDomain());
    }

    public List<String> generateRandomValuesSet(int size) {
        List<String> res = new ArrayList<>();

        Random random = new Random();

        int max = 100_000;
        int min = -100_000;
        for (int i = 0; i < size; i++) {
            int randomInt = random.nextInt(max - min + 1) + min; // Generates a random integer between min and max (inclusive).
            res.add(randomInt+"");
        }

        return res;
    }

    @Test
    public void testIsNumber() {
        boolean result = APICatalogSync.isNumber("a");
        assertFalse(result);

        result = APICatalogSync.isNumber("1000.32");
        assertFalse(result);

        result = APICatalogSync.isNumber("10000");
        assertTrue(result);

        result = APICatalogSync.isNumber("716520346144920");
        assertTrue(result);
    }

}
