package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.CustomDataType;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.EndsWithPredicate;
import com.akto.dto.data_types.RegexPredicate;
import com.akto.dto.data_types.StartsWithPredicate;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.utils.MongoBasedTest;
import com.mongodb.BasicDBObject;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestSingleTypeInfoDao extends MongoBasedTest {

    @Test
    public void testInsertAndFetchAktoDefined() {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                "url", "GET",200, false, "param#key", SingleTypeInfo.EMAIL, 0
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
        Context.accountId.set(1_000_000);
        Conditions keyConditions = new Conditions(Arrays.asList(new StartsWithPredicate("we"), new RegexPredicate("reg")), Conditions.Operator.AND);
        Conditions valueConditions = new Conditions(Collections.singletonList(new EndsWithPredicate("something")), Conditions.Operator.OR);
        CustomDataType customDataType = new CustomDataType(
                "custom1", false, Arrays.asList(SingleTypeInfo.Position.REQUEST_PAYLOAD, SingleTypeInfo.Position.RESPONSE_PAYLOAD),
                0,true, keyConditions, valueConditions, Conditions.Operator.OR
        );

        CustomDataTypeDao.instance.insertOne(customDataType);
        SingleTypeInfo.init();

        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                "url", "GET",200, false, "param#key", customDataType.toSubType(), 0
        );
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0,0,0);
        SingleTypeInfoDao.instance.updateOne(
                SingleTypeInfoDao.createFilters(singleTypeInfo), Updates.inc("count",1)
        );

        SingleTypeInfo singleTypeInfoFromDb = SingleTypeInfoDao.instance.findOne(new BasicDBObject());

        assertEquals(singleTypeInfoFromDb.getSubType(), customDataType.toSubType());

        SingleTypeInfoDao.instance.getMCollection().drop();
        CustomDataTypeDao.instance.getMCollection().drop();
    }


    private WriteModel<SingleTypeInfo> createSingleTypeInfoUpdate(String url, String method, SingleTypeInfo.SubType subType, int apiCollectionId, int responseCode) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                url, method,responseCode, false, "param", subType, apiCollectionId
        );
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0,0,0);
        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(true);
        return new UpdateOneModel<>(SingleTypeInfoDao.createFilters(singleTypeInfo), Updates.set("count",1),updateOptions);
    }

    @Test
    public void testFilterForSensitiveParamsExcludingUserMarkedSensitive() {
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

        SingleTypeInfo.init();

        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkWrites);

        List<SingleTypeInfo> singleTypeInfoList = SingleTypeInfoDao.instance.findAll(new BasicDBObject());
        assertEquals(singleTypeInfoList.size(),9);

        Bson filter = SingleTypeInfoDao.instance.filterForSensitiveParamsExcludingUserMarkedSensitive(null);

        List<SingleTypeInfo> sensitiveSingleTypeInfos = SingleTypeInfoDao.instance.findAll(filter);
        assertEquals(sensitiveSingleTypeInfos.size(), 6);


        Set<String> sensitiveEndpoints = SingleTypeInfoDao.instance.getSensitiveEndpoints(0);
        assertEquals(sensitiveEndpoints.size(), 4);

        SensitiveParamInfoDao.instance.insertOne(
                new SensitiveParamInfo("I", "GET", 200, false, "param", 0, true)
        );

        sensitiveEndpoints = SingleTypeInfoDao.instance.getSensitiveEndpoints(0);
        assertEquals(sensitiveEndpoints.size(), 5);
    }
}
