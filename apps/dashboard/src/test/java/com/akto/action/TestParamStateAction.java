package com.akto.action;

import com.akto.MongoBasedTest;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.types.CappedSet;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestParamStateAction extends MongoBasedTest {

    private UpdateOneModel<SingleTypeInfo> buildSingleTypeInfo(int publicCount, int uniqueCount, boolean isUrlParam, String param) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                "url1", "GET", -1, false, param, SingleTypeInfo.GENERIC,0, isUrlParam
        );
        SingleTypeInfo singleTypeInfo =  new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0,0,0, new CappedSet<>(), SingleTypeInfo.Domain.ANY, 0,0);
        singleTypeInfo.setUniqueCount(uniqueCount);
        singleTypeInfo.setPublicCount(publicCount);

        Bson updateKey = SingleTypeInfoDao.createFilters(singleTypeInfo);
        Bson update = Updates.combine(
                Updates.set(SingleTypeInfo._UNIQUE_COUNT, uniqueCount),
                Updates.set(SingleTypeInfo._PUBLIC_COUNT, publicCount)
        );
        return new UpdateOneModel<>(updateKey, update, new UpdateOptions().upsert(true));
    }

    @Test
    public void testFetchParamsStatus() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ArrayList<WriteModel<SingleTypeInfo>> bulkUpdates = new ArrayList<>();

        bulkUpdates.add( buildSingleTypeInfo(10, 20, false, "param1"));
        bulkUpdates.add( buildSingleTypeInfo(10, 200, false, "param2"));
        bulkUpdates.add( buildSingleTypeInfo(0, 0, false, "param3"));

        bulkUpdates.add( buildSingleTypeInfo(10, 20,true, "1"));
        bulkUpdates.add( buildSingleTypeInfo(10, 200,true, "2"));
        bulkUpdates.add( buildSingleTypeInfo(0, 0,true, "3"));

        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdates);

        ParamStateAction paramStateAction = new ParamStateAction();
        Context.userId.set(null);
        String result = paramStateAction.fetchParamsStatus();
        assertEquals("SUCCESS", result);

        assertEquals(2, paramStateAction.getPrivateSingleTypeInfo().size());
    }
}
