package com.akto.action;

import com.akto.MongoBasedTest;
import com.akto.dao.ParamTypeInfoDao;
import com.akto.dto.type.ParamTypeInfo;
import org.checkerframework.checker.units.qual.C;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestParamStateAction extends MongoBasedTest {

    private ParamTypeInfo buildParamTypeInfo(int publicCount, int uniqueCount, boolean isUrlParam, String param) {
        ParamTypeInfo paramTypeInfo =  new ParamTypeInfo(
                0, "url1", "GET", -1, false, false, "one"
        );

        paramTypeInfo.setUniqueCount(uniqueCount);
        paramTypeInfo.setPublicCount(publicCount);

        return paramTypeInfo;
    }

    @Test
    public void testFetchParamsStatus() {
        ParamTypeInfoDao.instance.getMCollection().drop();

        ParamTypeInfoDao.instance.insertOne(buildParamTypeInfo(10, 20, false, "param1"));
        ParamTypeInfoDao.instance.insertOne(buildParamTypeInfo(10, 200, false, "param2"));
        ParamTypeInfoDao.instance.insertOne(buildParamTypeInfo(0, 0, false, "param3"));

        ParamTypeInfoDao.instance.insertOne(buildParamTypeInfo(10, 20,true, "1"));
        ParamTypeInfoDao.instance.insertOne(buildParamTypeInfo(10, 200,true, "2"));
        ParamTypeInfoDao.instance.insertOne(buildParamTypeInfo(0, 0,true, "3"));


        ParamStateAction paramStateAction = new ParamStateAction();
        String result = paramStateAction.fetchParamsStatus();
        assertEquals("SUCCESS", result);

        assertEquals(2, paramStateAction.getParamTypeInfoList().size());
    }
}
