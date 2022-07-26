package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomDataType;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.EndsWithPredicate;
import com.akto.dto.data_types.RegexPredicate;
import com.akto.dto.data_types.StartsWithPredicate;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.types.CappedSet;
import com.akto.utils.MongoBasedTest;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.apache.commons.collections.list.SynchronizedList;
import org.bson.conversions.Bson;
import org.junit.Test;
import org.springframework.security.core.parameters.P;

import java.security.PolicySpi;
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
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0,0,0);
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
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0,0,0);
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
        Conditions keyConditions = new Conditions(Arrays.asList(new StartsWithPredicate("we"), new RegexPredicate("reg")), Conditions.Operator.AND);
        Conditions valueConditions = new Conditions(Collections.singletonList(new EndsWithPredicate("something")), Conditions.Operator.OR);
        CustomDataType customDataType = new CustomDataType(
                "custom1", false, Arrays.asList(SingleTypeInfo.Position.REQUEST_PAYLOAD, SingleTypeInfo.Position.RESPONSE_PAYLOAD),
                0,true, keyConditions, valueConditions, Conditions.Operator.OR
        );

        CustomDataTypeDao.instance.insertOne(customDataType);
        SingleTypeInfo.fetchCustomDataTypes();

        assertEquals(SingleTypeInfo.customDataTypeMap.keySet().size(), 1);

        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                "url", "GET",200, false, "param#key", customDataType.toSubType(), 0, false
        );
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0,0,0);
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
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 100,0,0);
        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(true);
        return new UpdateOneModel<>(SingleTypeInfoDao.createFilters(singleTypeInfo), Updates.set("count",1),updateOptions);
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

        CustomDataType customDataType1 = new CustomDataType("CUSTOM_DATA_1", true, Collections.emptyList(), 0,true, null,null, Conditions.Operator.AND);
        CustomDataType customDataType2 = new CustomDataType("CUSTOM_DATA_2",false, Collections.emptyList(), 0,true, null,null, Conditions.Operator.AND);
        CustomDataTypeDao.instance.insertMany(Arrays.asList(customDataType1, customDataType2));

        bulkWrites.add(createSingleTypeInfoUpdate("E", "POST",customDataType1.toSubType(), 0,200));
        bulkWrites.add(createSingleTypeInfoUpdate("F", "POST",customDataType2.toSubType(), 0,200));

        bulkWrites.add(createSingleTypeInfoUpdate("G", "POST", SingleTypeInfo.EMAIL, 0,-1));
        bulkWrites.add(createSingleTypeInfoUpdate("G", "GET", SingleTypeInfo.EMAIL, 0,-1));
        bulkWrites.add(createSingleTypeInfoUpdate("H", "GET", SingleTypeInfo.EMAIL, 1,-1));

        SingleTypeInfo.fetchCustomDataTypes();;

        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkWrites);

        List<SingleTypeInfo> singleTypeInfoList = SingleTypeInfoDao.instance.findAll(new BasicDBObject());
        assertEquals(singleTypeInfoList.size(),9);

        Bson filter = SingleTypeInfoDao.instance.filterForSensitiveParamsExcludingUserMarkedSensitive(null,null,null);

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
        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkWrites);

        List<ApiInfo.ApiInfoKey> apiInfoKeyList0 = SingleTypeInfoDao.instance.fetchEndpointsInCollection(0);
        List<ApiInfo.ApiInfoKey> apiInfoKeyList1 = SingleTypeInfoDao.instance.fetchEndpointsInCollection(1);

        assertEquals(apiInfoKeyList0.size(), 4);
        assertEquals(apiInfoKeyList1.size(), 2);

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
                Updates.addEachToSet(SingleTypeInfo.VALUES+".elements",Arrays.asList("a","b","c"))
        );

        List<SingleTypeInfo> singleTypeInfoList = SingleTypeInfoDao.instance.fetchAll();
        assertEquals(singleTypeInfoList.size(), 2);
        for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
            assertEquals(3, singleTypeInfo.getValues().getElements().size());
        }

        SingleTypeInfoDao.instance.deleteValues();
        singleTypeInfoList = SingleTypeInfoDao.instance.fetchAll();
        assertEquals(singleTypeInfoList.size(), 2);
        for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
            assertEquals(0, singleTypeInfo.getValues().getElements().size());
        }
    }
}
