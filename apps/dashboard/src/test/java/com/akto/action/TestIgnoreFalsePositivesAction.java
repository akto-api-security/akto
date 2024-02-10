package com.akto.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dto.type.AccountDataTypesInfo;
import org.checkerframework.checker.units.qual.A;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.AktoDataTypeDao;
import com.akto.dto.AktoDataType;
import com.akto.dto.IgnoreData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.SingleTypeInfo.ParamId;
import static org.junit.Assert.assertEquals;

public class TestIgnoreFalsePositivesAction extends MongoBasedTest{
    
    @Test
    public void test(){
        AktoDataTypeDao.instance.getMCollection().drop();
        AktoDataType aktoDataType = new AktoDataType("UUID", false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true);
        Set<String> ignored = new HashSet<>();
        ignored.add("something");
        aktoDataType.getIgnoreData().setIgnoredKeysInAllAPIs(ignored);
        AktoDataTypeDao.instance.insertOne(aktoDataType);
        IgnoreFalsePositivesAction ignoreTest = new IgnoreFalsePositivesAction();
        Map<String,IgnoreData> falsePositives = new HashMap<>();
        IgnoreData ignoreData = new IgnoreData();
        ignored.add("somethingelse");
        ignoreData.setIgnoredKeysInAllAPIs(ignored);
        Map<String, List<ParamId>> ignoredKeysInSelectedAPIs = new HashMap<>();
        ParamId paramId = new ParamId("api/STRING", "GET", -1, false, "someveryrandomUUID", SingleTypeInfo.UUID, 0, false);
        List<ParamId> paramIds = new ArrayList<>();
        paramIds.add(paramId);
        ignoredKeysInSelectedAPIs.put("someveryrandomUUID", paramIds);
        ignoreData.setIgnoredKeysInSelectedAPIs(ignoredKeysInSelectedAPIs);
        falsePositives.put("UUID",ignoreData);
        ignoreTest.setFalsePositives(falsePositives);
        AccountDataTypesInfo info = SingleTypeInfo.getAccountToDataTypesInfo().get(ACCOUNT_ID);
        if (info == null) {
            info = new AccountDataTypesInfo();
        }
        Map<String, AktoDataType> aktoDataTypeMap = new HashMap<>();
        aktoDataTypeMap.put("UUID",aktoDataType);
        info.setAktoDataTypeMap(aktoDataTypeMap);
        SingleTypeInfo.getAccountToDataTypesInfo().put(ACCOUNT_ID, info);

        String result = ignoreTest.setFalsePositivesInSensitiveData();
        aktoDataType = AktoDataTypeDao.instance.findOne("name","UUID");
        assertEquals("SUCCESS",result);
        assertEquals(2,aktoDataType.getIgnoreData().getIgnoredKeysInAllAPIs().size());
        assertEquals(1,aktoDataType.getIgnoreData().getIgnoredKeysInSelectedAPIs().size());
    }
}