package com.akto.dao;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomDataType;
import com.akto.dto.IgnoreData;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.EndsWithPredicate;
import com.akto.dto.data_types.RegexPredicate;
import com.akto.dto.data_types.StartsWithPredicate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.types.CappedSet;
import com.akto.utils.MongoBasedTest;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestSingleTypeInfoDao extends MongoBasedTest {

    // testDefaultDomain to test if single type info in db has domain value null then if default gets set to ENUM or not
    @Test
    public void testDefaultDomain() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                "url", "GET",200, false, "param#key", SingleTypeInfo.EMAIL, 0, false
        );
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0,0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        SingleTypeInfoDao.instance.updateOne(
                SingleTypeInfoDao.createFilters(singleTypeInfo), Updates.inc("count",1)
        );

        SingleTypeInfo singleTypeInfoFromDb = SingleTypeInfoDao.instance.findOne(new BasicDBObject());
        assertEquals(SingleTypeInfo.Domain.ENUM, singleTypeInfoFromDb.getDomain());

    }

    @Test
    public void testInsertAndFetchAktoDefined() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                "url", "GET",200, false, "param#key", SingleTypeInfo.EMAIL, 0, false
        );
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0,0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        SingleTypeInfoDao.instance.updateOne(
                SingleTypeInfoDao.createFilters(singleTypeInfo), Updates.inc("count",1)
        );

        SingleTypeInfo singleTypeInfoFromDb = SingleTypeInfoDao.instance.findOne(new BasicDBObject());

        assertEquals(singleTypeInfoFromDb.getSubType(), SingleTypeInfo.EMAIL);
    }

    @Test
    public void testInsertAndFetchCustom() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        CustomDataTypeDao.instance.getMCollection().drop();
        Context.accountId.set(1_000_000);
        IgnoreData ignoreData = new IgnoreData(new HashMap<>(), new HashSet<>());
        Conditions keyConditions = new Conditions(Arrays.asList(new StartsWithPredicate("we"), new RegexPredicate("reg")), Conditions.Operator.AND);
        Conditions valueConditions = new Conditions(Collections.singletonList(new EndsWithPredicate("something")), Conditions.Operator.OR);
        CustomDataType customDataType = new CustomDataType(
                "custom1", false, Arrays.asList(SingleTypeInfo.Position.REQUEST_PAYLOAD, SingleTypeInfo.Position.RESPONSE_PAYLOAD),
                0,true, keyConditions, valueConditions, Conditions.Operator.OR,ignoreData, false, true
        );

        CustomDataTypeDao.instance.insertOne(customDataType);
        SingleTypeInfo.fetchCustomDataTypes(Context.accountId.get());

        assertEquals(SingleTypeInfo.getCustomDataTypeMap(Context.accountId.get()).keySet().size(), 1);

        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                "url", "GET",200, false, "param#key", customDataType.toSubType(), 0, false
        );
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0,0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        SingleTypeInfoDao.instance.updateOne(
                SingleTypeInfoDao.createFilters(singleTypeInfo), Updates.inc("count",1)
        );


        SingleTypeInfo singleTypeInfoFromDb = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(singleTypeInfo));

        assertEquals(singleTypeInfoFromDb.getParam(), "param#key");
        assertEquals(singleTypeInfoFromDb.getSubType(), customDataType.toSubType());

    }


    private WriteModel<SingleTypeInfo> createSingleTypeInfoUpdate(String url, String method, SingleTypeInfo.SubType subType, int apiCollectionId, int responseCode) {
        SingleTypeInfoDao.instance.getMCollection().drop();
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                url, method,responseCode, false, "param", subType, apiCollectionId, false
        );
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 100,0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(true);
        return new UpdateOneModel<>(SingleTypeInfoDao.createFilters(singleTypeInfo), Updates.combine(Updates.set("count",1),Updates.set("timestamp",singleTypeInfo.getTimestamp()), Updates.set(SingleTypeInfo._COLLECTION_IDS, singleTypeInfo.getCollectionIds())),updateOptions);
    }

    @Test
    public void testFilterForSensitiveParamsExcludingUserMarkedSensitive() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        CustomDataTypeDao.instance.getMCollection().drop();
        Context.accountId.set(1_000_000);
        List<WriteModel<SingleTypeInfo>> bulkWrites = new ArrayList<>();
        bulkWrites.add(createSingleTypeInfoUpdate("A", "GET", SingleTypeInfo.EMAIL, 0,200));
        bulkWrites.add(createSingleTypeInfoUpdate("B", "POST", SingleTypeInfo.INTEGER_32, 0,200));
        bulkWrites.add(createSingleTypeInfoUpdate("C", "GET", SingleTypeInfo.JWT, 0,200));
        bulkWrites.add(createSingleTypeInfoUpdate("D", "POST", SingleTypeInfo.JWT, 0,-1));
        IgnoreData ignoreData = new IgnoreData(new HashMap<>(), new HashSet<>());
        CustomDataType customDataType1 = new CustomDataType("CUSTOM_DATA_1", true, Collections.emptyList(), 0,true, null,null, Conditions.Operator.AND,ignoreData, false, true);
        CustomDataType customDataType2 = new CustomDataType("CUSTOM_DATA_2",false, Collections.emptyList(), 0,true, null,null, Conditions.Operator.AND,ignoreData, false, true);
        CustomDataTypeDao.instance.insertMany(Arrays.asList(customDataType1, customDataType2));

        bulkWrites.add(createSingleTypeInfoUpdate("E", "POST",customDataType1.toSubType(), 0,200));
        bulkWrites.add(createSingleTypeInfoUpdate("F", "POST",customDataType2.toSubType(), 0,200));

        bulkWrites.add(createSingleTypeInfoUpdate("G", "POST", SingleTypeInfo.EMAIL, 0,-1));
        bulkWrites.add(createSingleTypeInfoUpdate("G", "GET", SingleTypeInfo.EMAIL, 0,-1));
        bulkWrites.add(createSingleTypeInfoUpdate("H", "GET", SingleTypeInfo.EMAIL, 1,-1));

        SingleTypeInfo.fetchCustomDataTypes(Context.accountId.get());;

        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkWrites);

        List<SingleTypeInfo> singleTypeInfoList = SingleTypeInfoDao.instance.findAll(new BasicDBObject());
        assertEquals(singleTypeInfoList.size(),9);

        Bson filter = SingleTypeInfoDao.instance.filterForSensitiveParamsExcludingUserMarkedSensitive(null,null,null, null);

        List<SingleTypeInfo> sensitiveSingleTypeInfos = SingleTypeInfoDao.instance.findAll(filter);
        assertEquals(sensitiveSingleTypeInfos.size(), 6);


        Set<String> sensitiveEndpoints = SingleTypeInfoDao.instance.getSensitiveEndpoints(0, null, null);
        assertEquals(sensitiveEndpoints.size(), 4);

        SensitiveParamInfoDao.instance.insertOne(
                new SensitiveParamInfo("I", "GET", 200, false, "param", 0, true)
        );

        sensitiveEndpoints = SingleTypeInfoDao.instance.getSensitiveEndpoints(0, null, null);
        assertEquals(sensitiveEndpoints.size(), 5);
    }

    @Test
    public void testFilterForAllNewParams(){
        SingleTypeInfoDao.instance.getMCollection().drop();

        List<WriteModel<SingleTypeInfo>> bulkWrites = new ArrayList<>();
        bulkWrites.add(createSingleTypeInfoUpdate("A", "GET", SingleTypeInfo.EMAIL, 0,200));
        bulkWrites.add(createSingleTypeInfoUpdate("B", "GET", SingleTypeInfo.EMAIL, 0,200));
        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkWrites);

        Bson filterNewParams = SingleTypeInfoDao.instance.filterForAllNewParams(0,0);

        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(filterNewParams);
        assertEquals(list.size(),2);
    }

    @Test
    public void testResetCount() {
        SingleTypeInfoDao.instance.getMCollection().drop();

        List<WriteModel<SingleTypeInfo>> bulkWrites = new ArrayList<>();
        bulkWrites.add(createSingleTypeInfoUpdate("A", "GET", SingleTypeInfo.EMAIL, 0,200));
        bulkWrites.add(createSingleTypeInfoUpdate("B", "GET", SingleTypeInfo.EMAIL, 0,200));
        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkWrites);

        SingleTypeInfoDao.instance.resetCount();

        List<SingleTypeInfo> singleTypeInfoList = SingleTypeInfoDao.instance.findAll(new BasicDBObject());

        for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
            if (singleTypeInfo.getCount() != 0) {
                fail();
            }
        }

    }

    @Test
    public void testFetchEndpointsInCollection() {
        SingleTypeInfoDao.instance.getMCollection().drop();

        List<WriteModel<SingleTypeInfo>> bulkWrites = new ArrayList<>();
        bulkWrites.add(createSingleTypeInfoUpdate("A", "GET", SingleTypeInfo.EMAIL, 0,200));
        bulkWrites.add(createSingleTypeInfoUpdate("A", "GET", SingleTypeInfo.GENERIC, 0,200));
        bulkWrites.add(createSingleTypeInfoUpdate("A", "GET", SingleTypeInfo.EMAIL, 0,201));
        bulkWrites.add(createSingleTypeInfoUpdate("A", "POST", SingleTypeInfo.EMAIL, 0,200));
        bulkWrites.add(createSingleTypeInfoUpdate("B", "POST", SingleTypeInfo.EMAIL, 0,200));
        bulkWrites.add(createSingleTypeInfoUpdate("B", "GET", SingleTypeInfo.EMAIL, 0,200));
        bulkWrites.add(createSingleTypeInfoUpdate("A", "GET", SingleTypeInfo.EMAIL, 1,200));
        bulkWrites.add(createSingleTypeInfoUpdate("C", "GET", SingleTypeInfo.EMAIL, 1,200));
        bulkWrites.add(createSingleTypeInfoUpdate("A", "GET", SingleTypeInfo.EMAIL, -1000,200));
        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkWrites);

        List<ApiInfo.ApiInfoKey> apiInfoKeyList0 = SingleTypeInfoDao.instance.fetchEndpointsInCollection(0);
        List<ApiInfo.ApiInfoKey> apiInfoKeyList1 = SingleTypeInfoDao.instance.fetchEndpointsInCollection(1);
        List<ApiInfo.ApiInfoKey> apiInfoKeyList2 = SingleTypeInfoDao.instance.fetchEndpointsInCollection(-1);
        List<ApiInfo.ApiInfoKey> apiInfoKeyList3 = SingleTypeInfoDao.instance.fetchEndpointsInCollection(-1000);

        assertEquals(apiInfoKeyList0.size(), 4);
        assertEquals(apiInfoKeyList1.size(), 2);
        assertEquals(apiInfoKeyList2.size(), 7);
        assertEquals(apiInfoKeyList3.size(), 1);

    }

    @Test
    public void testDeleteValues() {
        SingleTypeInfoDao.instance.getMCollection().drop();

        List<WriteModel<SingleTypeInfo>> bulkWrites = new ArrayList<>();
        bulkWrites.add(createSingleTypeInfoUpdate("A", "GET", SingleTypeInfo.EMAIL, 0,200));
        bulkWrites.add(createSingleTypeInfoUpdate("A", "GET", SingleTypeInfo.GENERIC, 0,200));
        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkWrites);

        SingleTypeInfoDao.instance.getMCollection().updateMany(
                new BasicDBObject(),
                Updates.addEachToSet(SingleTypeInfo._VALUES +".elements",Arrays.asList("a","b","c"))
        );

        List<SingleTypeInfo> singleTypeInfoList = SingleTypeInfoDao.instance.fetchAll();
        assertEquals(2, singleTypeInfoList.size());
        for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
            assertEquals(3, singleTypeInfo.getValues().getElements().size());
        }

        SingleTypeInfoDao.instance.deleteValues();
        singleTypeInfoList = SingleTypeInfoDao.instance.fetchAll();
        assertEquals(2, singleTypeInfoList.size());
        for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
            assertEquals(0, singleTypeInfo.getValues().getElements().size());
        }
    }

    @Test
    public void testCreateFiltersWithoutSubType() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        // insert 2 identical params but without different subType. Then update using createFiltersWithoutSubType

        SingleTypeInfo.ParamId paramId1 = new SingleTypeInfo.ParamId("url", "GET", 200, false, "param1", SingleTypeInfo.GENERIC, 0, false);
        SingleTypeInfo sti1 = new SingleTypeInfo(paramId1, new HashSet<>(), new HashSet<>(), 0, 0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);

        SingleTypeInfo.ParamId paramId2 = new SingleTypeInfo.ParamId("url", "GET", 200, false, "param1", SingleTypeInfo.EMAIL, 0, false);
        SingleTypeInfo sti2 = new SingleTypeInfo(paramId2, new HashSet<>(), new HashSet<>(), 0, 0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);

        SingleTypeInfo.ParamId paramId3 = new SingleTypeInfo.ParamId("url_next", "GET", 200, false, "param1", SingleTypeInfo.EMAIL, 0, false);
        SingleTypeInfo sti3 = new SingleTypeInfo(paramId3, new HashSet<>(), new HashSet<>(), 0, 0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);

        SingleTypeInfo.ParamId paramId4 = new SingleTypeInfo.ParamId("url", "POST", 200, false, "param1", SingleTypeInfo.EMAIL, 0, false);
        SingleTypeInfo sti4 = new SingleTypeInfo(paramId4, new HashSet<>(), new HashSet<>(), 0, 0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);

        SingleTypeInfo.ParamId paramId5 = new SingleTypeInfo.ParamId("url", "GET",200,true, "param1", SingleTypeInfo.EMAIL, 0, false);
        SingleTypeInfo sti5 = new SingleTypeInfo(paramId5, new HashSet<>(), new HashSet<>(), 0, 0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);

        SingleTypeInfo.ParamId paramId6 = new SingleTypeInfo.ParamId("url", "GET",200,false, "param1", SingleTypeInfo.EMAIL, 0,true);
        SingleTypeInfo sti6 = new SingleTypeInfo(paramId6, new HashSet<>(), new HashSet<>(), 0, 0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);


        List<UpdateOneModel<SingleTypeInfo>> bulkWrites = new ArrayList<>();
        bulkWrites.add(new UpdateOneModel<>(SingleTypeInfoDao.createFilters(sti1), Updates.set("count",1), new UpdateOptions().upsert(true)));
        bulkWrites.add(new UpdateOneModel<>(SingleTypeInfoDao.createFilters(sti2), Updates.set("count",1), new UpdateOptions().upsert(true)));
        bulkWrites.add(new UpdateOneModel<>(SingleTypeInfoDao.createFilters(sti3), Updates.set("count",1), new UpdateOptions().upsert(true)));
        bulkWrites.add(new UpdateOneModel<>(SingleTypeInfoDao.createFilters(sti4), Updates.set("count",1), new UpdateOptions().upsert(true)));
        bulkWrites.add(new UpdateOneModel<>(SingleTypeInfoDao.createFilters(sti5), Updates.set("count",1), new UpdateOptions().upsert(true)));
        bulkWrites.add(new UpdateOneModel<>(SingleTypeInfoDao.createFilters(sti6), Updates.set("count",1), new UpdateOptions().upsert(true)));

        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkWrites);

        List<UpdateManyModel<SingleTypeInfo>> bulkWritesMany = new ArrayList<>();
        // only 1 update will update both params since only subType is different
        // but won't update STIs other than sti1 and sti2
        bulkWritesMany.add(new UpdateManyModel<>(SingleTypeInfoDao.createFiltersWithoutSubType(sti1), Updates.set("count",100), new UpdateOptions().upsert(false)));
        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkWritesMany);

        int count = 0;
        List<SingleTypeInfo> singleTypeInfos = SingleTypeInfoDao.instance.findAll(new BasicDBObject());
        for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
            if (singleTypeInfo.equals(sti1) || singleTypeInfo.equals(sti2)) {
                count ++ ;
                assertEquals(100,singleTypeInfo.getCount());
            } else {
                assertEquals(1, singleTypeInfo.getCount());
            }
        }

        assertEquals(bulkWrites.size(), singleTypeInfos.size());
        assertEquals(2, count);
    }

    // Test to see what happens when singleTypeInfo
    @Test
    public void testAddingIsUrlParam() {
        SingleTypeInfoDao.instance.getMCollection().drop();

        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                "url", "GET",200, false, "param#key", SingleTypeInfo.EMAIL, 0, false
        );
        SingleTypeInfo info = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0,0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);

        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("url", info.getUrl()));
        filters.add(Filters.eq("method", info.getMethod()));
        filters.add(Filters.eq("responseCode", info.getResponseCode()));
        filters.add(Filters.eq("isHeader", info.getIsHeader()));
        filters.add(Filters.eq("param", info.getParam()));
        filters.add(Filters.eq("apiCollectionId", info.getApiCollectionId()));
        filters.add(Filters.eq("subType", info.getSubType().getName()));

        SingleTypeInfoDao.instance.updateOne(Filters.and(filters), Updates.set("count",1));
        long count = SingleTypeInfoDao.instance.getMCollection().countDocuments();
        assertEquals(1, count);
        // to make sure isUrlParam doesn't exist
        SingleTypeInfo stiFromDb = SingleTypeInfoDao.instance.findOne(Filters.exists(SingleTypeInfo._IS_URL_PARAM));
        assertNull(stiFromDb);

        SingleTypeInfoDao.instance.updateOne(SingleTypeInfoDao.createFilters(info), Updates.set("count",1));
        count = SingleTypeInfoDao.instance.getMCollection().countDocuments();
        assertEquals(1, count);

        info.setIsUrlParam(true);
        SingleTypeInfoDao.instance.updateOne(SingleTypeInfoDao.createFilters(info), Updates.set("count",1));
        count = SingleTypeInfoDao.instance.getMCollection().countDocuments();
        assertEquals(2, count);
    }

    @Test
    public void testInsert() {
        SingleTypeInfoDao.instance.getMCollection().drop();

        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                "url", "GET",200, false, "param#key", SingleTypeInfo.EMAIL, 0, false
        );
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 100,1000,30, new CappedSet<>(), SingleTypeInfo.Domain.RANGE, -1000, 10000);
        singleTypeInfo.setUniqueCount(1000);
        singleTypeInfo.setPublicCount(100);

        SingleTypeInfoDao.instance.insertOne(singleTypeInfo);

        SingleTypeInfo singleTypeInfoFromDb = SingleTypeInfoDao.instance.findOne(new BasicDBObject());
        assertEquals(singleTypeInfo, singleTypeInfoFromDb);

        assertEquals(singleTypeInfo.getMaxValue(), singleTypeInfoFromDb.getMaxValue());
        assertEquals(singleTypeInfo.getMinValue(), singleTypeInfoFromDb.getMinValue());
        assertEquals(singleTypeInfo.getUniqueCount(), singleTypeInfoFromDb.getUniqueCount());
        assertEquals(singleTypeInfo.getPublicCount(), singleTypeInfoFromDb.getPublicCount());
        assertEquals(singleTypeInfo.getCount(), singleTypeInfoFromDb.getCount());
        assertEquals(singleTypeInfo.getValues().count(), singleTypeInfoFromDb.getValues().count());
    }

    @Test
    public void testFetchStiOfCollections() {
        SingleTypeInfoDao.instance.getMCollection().drop();

        SingleTypeInfo sti1 = generateSTIUsingCollectionId(1000, "url1", "param1");
        SingleTypeInfo sti2 = generateSTIUsingCollectionId(1000, "url1", "param2");
        SingleTypeInfo sti3 = generateSTIUsingCollectionId(2000, "url2", "param3");
        SingleTypeInfo sti4 = generateSTIUsingCollectionId(2000, "url3", "param4");
        SingleTypeInfo sti5 = generateSTIUsingCollectionId(3000, "url4", "param4");

        SingleTypeInfoDao.instance.insertMany(Arrays.asList(sti1, sti2, sti3, sti4, sti5));

        List<SingleTypeInfo> singleTypeInfos = SingleTypeInfoDao.instance.fetchStiOfCollections(Arrays.asList(1000,3000));

        assertEquals(3, singleTypeInfos.size());
        Map<Integer, Integer> countMap = new HashMap<>();
        for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
            int c = countMap.getOrDefault(singleTypeInfo.getApiCollectionId(), 0);
            countMap.put(singleTypeInfo.getApiCollectionId(), c+1);
        }

        assertEquals(2,(int) countMap.get(1000));
        assertNull(countMap.get(2000));
        assertEquals(1,(int) countMap.get(3000));
    }


    private SingleTypeInfo generateSTIUsingCollectionId(int apiCollectionId, String url, String param) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                url, "GET",200, false, param, SingleTypeInfo.GENERIC, apiCollectionId, false
        );
        return new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 100,1000,30, new CappedSet<>(), SingleTypeInfo.Domain.RANGE, -1000, 10000);
    }

    @Test
    public void testFetchRequestParameters() {
        SingleTypeInfoDao.instance.getMCollection().drop();

        SingleTypeInfo.ParamId paramId1 = new SingleTypeInfo.ParamId("/api/books", "GET",-1, false, "param_req_1", SingleTypeInfo.GENERIC, 1000, false);
        SingleTypeInfo sti1 = new SingleTypeInfo(paramId1, new HashSet<>(), new HashSet<>(), 100,1000,30, new CappedSet<>(), SingleTypeInfo.Domain.RANGE, -1000, 10000);

        SingleTypeInfo.ParamId paramId2 = new SingleTypeInfo.ParamId("/api/books", "GET",-1, false, "param_req_2", SingleTypeInfo.GENERIC, 1000, false);
        SingleTypeInfo sti2 = new SingleTypeInfo(paramId2, new HashSet<>(), new HashSet<>(), 100,1000,30, new CappedSet<>(), SingleTypeInfo.Domain.RANGE, -1000, 10000);

        SingleTypeInfo.ParamId paramId3 = new SingleTypeInfo.ParamId("/api/books", "GET",200, false, "param_resp_1", SingleTypeInfo.GENERIC, 1000, false);
        SingleTypeInfo sti3 = new SingleTypeInfo(paramId3, new HashSet<>(), new HashSet<>(), 100,1000,30, new CappedSet<>(), SingleTypeInfo.Domain.RANGE, -1000, 10000);

        SingleTypeInfo.ParamId paramId4 = new SingleTypeInfo.ParamId("/api/cars", "POST",-1, false, "param_req_2", SingleTypeInfo.GENERIC, 1000, false);
        SingleTypeInfo sti4 = new SingleTypeInfo(paramId4, new HashSet<>(), new HashSet<>(), 100,1000,30, new CappedSet<>(), SingleTypeInfo.Domain.RANGE, -1000, 10000);

        SingleTypeInfo.ParamId paramId5 = new SingleTypeInfo.ParamId("/api/cars", "POST",200, false, "param_resp_2", SingleTypeInfo.GENERIC, 1000, false);
        SingleTypeInfo sti5 = new SingleTypeInfo(paramId5, new HashSet<>(), new HashSet<>(), 100,1000,30, new CappedSet<>(), SingleTypeInfo.Domain.RANGE, -1000, 10000);

        SingleTypeInfo.ParamId paramId6 = new SingleTypeInfo.ParamId("/api/cars", "GET",-1, false, "param_req_3", SingleTypeInfo.GENERIC, 1000, false);
        SingleTypeInfo sti6 = new SingleTypeInfo(paramId6, new HashSet<>(), new HashSet<>(), 100,1000,30, new CappedSet<>(), SingleTypeInfo.Domain.RANGE, -1000, 10000);

        SingleTypeInfo.ParamId paramId7 = new SingleTypeInfo.ParamId("/api/cars", "GET",-1, false, "param_req_3", SingleTypeInfo.GENERIC,999, false);
        SingleTypeInfo sti7 = new SingleTypeInfo(paramId7, new HashSet<>(), new HashSet<>(), 100,1000,30, new CappedSet<>(), SingleTypeInfo.Domain.RANGE, -1000, 10000);

        SingleTypeInfoDao.instance.insertMany(Arrays.asList(sti1, sti2, sti3, sti4, sti5, sti6, sti7));

        assertEquals(7, SingleTypeInfoDao.instance.getEstimatedCount());

        List<ApiInfo.ApiInfoKey> apiInfoKeys = new ArrayList<>();
        apiInfoKeys.add(new ApiInfo.ApiInfoKey(1000, "/api/books", URLMethods.Method.GET));
        apiInfoKeys.add(new ApiInfo.ApiInfoKey(1000, "/api/cars", URLMethods.Method.POST));
        Map<ApiInfo.ApiInfoKey, List<String>> apiInfoKeyListMap = SingleTypeInfoDao.instance.fetchRequestParameters(apiInfoKeys);

        assertEquals(2, apiInfoKeyListMap.size());
        assertEquals(2, apiInfoKeyListMap.get(new ApiInfo.ApiInfoKey(1000, "/api/books", URLMethods.Method.GET)).size());
        assertEquals(1, apiInfoKeyListMap.get(new ApiInfo.ApiInfoKey(1000, "/api/cars", URLMethods.Method.POST)).size());
    }
}
